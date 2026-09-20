package com.inigmasgames.hytalerpg.ui.skilltree;

import com.inigmasgames.canvasui.api.CanvasSnapshot;
import com.inigmasgames.canvasui.api.EdgeStyle;
import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

/** Durable presentation-topology binding. Gameplay edges remain coordinate-free. */
public final class SkillTreePortBindingStore {
    private static final Set<String> JOINT_PORTS=Set.of("a","b","c");
    private final Path directory;
    public SkillTreePortBindingStore(Path directory){this.directory=directory;}

    public synchronized Binding resolve(UUID player,LinkEdge edge,List<LinkEdge> all){
        Properties values=load(player);String key=edge.edgeId().value();
        Binding stored=new Binding(values.getProperty(key+".source",""),values.getProperty(key+".target",""));
        Set<String> sourceUsed=used(values,all,edge.edgeId().value(),edge.sourceNodeId(),true);
        Set<String> targetUsed=used(values,all,edge.edgeId().value(),edge.targetNodeId(),false);
        String source=valid(edge.sourceNodeId(),stored.sourcePort(),sourceUsed)?stored.sourcePort():allocate(edge.sourceNodeId(),sourceUsed,true);
        String target=valid(edge.targetNodeId(),stored.targetPort(),targetUsed)?stored.targetPort():allocate(edge.targetNodeId(),targetUsed,false);
        Binding resolved=new Binding(source,target);
        if(!resolved.equals(stored)){values.setProperty(key+".source",source);values.setProperty(key+".target",target);save(player,values);}
        return resolved;
    }

    public synchronized void bind(UUID player,LinkEdge edge,String sourcePort,String targetPort){
        if(!validFor(edge.sourceNodeId(),sourcePort,true)||!validFor(edge.targetNodeId(),targetPort,false))
            throw new IllegalArgumentException("INVALID_SKILLTREE_PORT_BINDING");
        Properties values=load(player);String key=edge.edgeId().value();
        if(sourcePort.equals(values.getProperty(key+".source"))&&targetPort.equals(values.getProperty(key+".target"))
                &&edge.sourceNodeId().externalId().equals(values.getProperty(key+".sourceNode"))
                &&edge.targetNodeId().externalId().equals(values.getProperty(key+".targetNode"))
                &&"false".equals(values.getProperty(key+".dormant")))return;
        values.setProperty(key+".source",sourcePort);values.setProperty(key+".target",targetPort);
        values.setProperty(key+".sourceNode",edge.sourceNodeId().externalId());
        values.setProperty(key+".targetNode",edge.targetNodeId().externalId());
        values.setProperty(key+".dormant","false");save(player,values);
    }

    public synchronized void preserveForClear(UUID player,LinkNodeId node,CanvasSnapshot snapshot){
        Properties values=load(player);
        for(CanvasSnapshot.EdgeState edge:snapshot.edges())if(edge.sourceNodeId().equals(node.externalId())||edge.targetNodeId().equals(node.externalId())){
            String key=edge.edgeId();values.setProperty(key+".source",edge.sourcePortId());values.setProperty(key+".target",edge.targetPortId());
            values.setProperty(key+".sourceNode",edge.sourceNodeId());values.setProperty(key+".targetNode",edge.targetNodeId());values.setProperty(key+".dormant","true");
        }
        save(player,values);
    }

    public synchronized void preserveDormant(UUID player,CanvasSnapshot snapshot,Set<String> edgeIds){
        Properties values=load(player);
        for(CanvasSnapshot.EdgeState edge:snapshot.edges())if(edgeIds.contains(edge.edgeId())){
            String key=edge.edgeId();values.setProperty(key+".source",edge.sourcePortId());values.setProperty(key+".target",edge.targetPortId());
            values.setProperty(key+".sourceNode",edge.sourceNodeId());values.setProperty(key+".targetNode",edge.targetNodeId());
            values.setProperty(key+".dormant","true");
        }
        save(player,values);
    }

    public synchronized List<CanvasSnapshot.EdgeState> states(UUID player,List<LinkEdge> live){
        Properties values=load(player);List<CanvasSnapshot.EdgeState> result=new ArrayList<>();Set<String> liveIds=new HashSet<>();
        for(LinkEdge edge:live){Binding binding=resolve(player,edge,live);bind(player,edge,binding.sourcePort(),binding.targetPort());liveIds.add(edge.edgeId().value());
            result.add(new CanvasSnapshot.EdgeState(edge.edgeId().value(),edge.sourceNodeId().externalId(),binding.sourcePort(),edge.targetNodeId().externalId(),binding.targetPort(),EdgeStyle.standard("rpg-parent")));}
        values=load(player);
        for(String name:values.stringPropertyNames())if(name.endsWith(".dormant")&&Boolean.parseBoolean(values.getProperty(name))){
            String id=name.substring(0,name.length()-".dormant".length());if(liveIds.contains(id))continue;
            String sourceNode=values.getProperty(id+".sourceNode","");String targetNode=values.getProperty(id+".targetNode","");
            String source=values.getProperty(id+".source","");String target=values.getProperty(id+".target","");
            try{LinkNodeId.parse(sourceNode);LinkNodeId.parse(targetNode);result.add(new CanvasSnapshot.EdgeState(id,sourceNode,source,targetNode,target,EdgeStyle.standard("rpg-parent")));}
            catch(IllegalArgumentException ignored){}
        }
        return List.copyOf(result);
    }

    public synchronized List<DormantEdge> dormant(UUID player,LinkNodeId node){
        Properties values=load(player);List<DormantEdge> result=new ArrayList<>();
        for(String name:values.stringPropertyNames())if(name.endsWith(".dormant")&&Boolean.parseBoolean(values.getProperty(name))){
            String id=name.substring(0,name.length()-".dormant".length());
            try{var edge=new DormantEdge(id,LinkNodeId.parse(values.getProperty(id+".sourceNode")),values.getProperty(id+".source"),
                    LinkNodeId.parse(values.getProperty(id+".targetNode")),values.getProperty(id+".target"));
                if(edge.source()==node||edge.target()==node)result.add(edge);
            }catch(IllegalArgumentException|NullPointerException ignored){}
        }
        return List.copyOf(result);
    }

    public synchronized void remove(UUID player,String edgeId){Properties values=load(player);boolean changed=false;
        for(String suffix:List.of(".source",".target",".sourceNode",".targetNode",".dormant"))changed|=values.remove(edgeId+suffix)!=null;
        if(changed)save(player,values);
    }

    public synchronized void clear(UUID player){
        try{Files.deleteIfExists(path(player));}
        catch(IOException error){throw new IllegalStateException("SKILLTREE_PORT_BINDING_CLEAR_FAILED",error);}
    }

    public synchronized void prune(UUID player,List<LinkEdge> edges){
        Properties values=load(player);Set<String> live=new HashSet<>();edges.forEach(e->live.add(e.edgeId().value()));
        boolean changed=false;
        for(String name:List.copyOf(values.stringPropertyNames())){
            int separator=name.lastIndexOf('.');
            String id=separator<=0?"":name.substring(0,separator);
            if(separator<=0||!live.contains(id)&&!Boolean.parseBoolean(values.getProperty(id+".dormant","false"))){values.remove(name);changed=true;}
        }
        if(changed)save(player,values);
    }

    private Set<String> used(Properties values,List<LinkEdge> all,String excluded,LinkNodeId joint,boolean source){
        Set<String> used=new HashSet<>();if(joint.kind()!=LinkNodeId.NodeKind.JOINT)return used;
        for(LinkEdge edge:all){if(edge.edgeId().value().equals(excluded))continue;String key=edge.edgeId().value();
            if(edge.sourceNodeId()==joint)used.add(values.getProperty(key+".source",""));
            if(edge.targetNodeId()==joint)used.add(values.getProperty(key+".target",""));}
        used.removeIf(v->!JOINT_PORTS.contains(v));return used;
    }
    private static boolean valid(LinkNodeId node,String port,Set<String> used){return validFor(node,port,node.kind()!=LinkNodeId.NodeKind.SKILL)&&!used.contains(port);}
    private static boolean validFor(LinkNodeId node,String port,boolean source){return switch(node.kind()){
        case SKILL -> !source&&"in".equals(port);case PASSIVE -> source&&"out".equals(port);case JOINT -> JOINT_PORTS.contains(port);};}
    private static String allocate(LinkNodeId node,Set<String> used,boolean source){
        if(node.kind()==LinkNodeId.NodeKind.SKILL)return "in";if(node.kind()==LinkNodeId.NodeKind.PASSIVE)return "out";
        for(String port:List.of("a","b","c"))if(!used.contains(port))return port;throw new IllegalStateException(node.externalId()+" has no free presentation port");
    }
    private Properties load(UUID player){Properties values=new Properties();Path path=path(player);if(!Files.isRegularFile(path))return values;
        try(var input=Files.newInputStream(path)){values.load(input);return values;}catch(IOException e){throw new IllegalStateException("SKILLTREE_PORT_BINDING_LOAD_FAILED",e);}}
    private void save(UUID player,Properties values){Path target=path(player),temp=target.resolveSibling(target.getFileName()+".tmp");
        try{Files.createDirectories(directory);var out=new java.io.ByteArrayOutputStream();values.store(out,"Hywind Skill Tree port bindings");byte[] bytes=out.toByteArray();
            try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException e){try{Files.deleteIfExists(temp);}catch(IOException ignored){}throw new IllegalStateException("SKILLTREE_PORT_BINDING_SAVE_FAILED",e);}}
    private Path path(UUID player){return directory.resolve(player+".properties");}
    public record Binding(String sourcePort,String targetPort){}
    public record DormantEdge(String edgeId,LinkNodeId source,String sourcePort,LinkNodeId target,String targetPort){}
}
