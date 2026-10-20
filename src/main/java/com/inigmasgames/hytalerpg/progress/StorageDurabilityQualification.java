package com.inigmasgames.hytalerpg.progress;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** Portable JDK-only qualification. Run this source file directly with Java 25; no RPG/Hytale
 * objects, codecs, checkpoint worker or classpath are required. Every raw barrier is retained. */
public final class StorageDurabilityQualification {
    public record Statistics(int samples,double p50Ms,double p90Ms,double p95Ms,double p99Ms,double p999Ms,double maxMs,
                             long above4Ms,long above8Ms,long above16Ms,long above50Ms,long above100Ms) {}
    public static Statistics statistics(long[] nanos){
        if(nanos.length==0||Arrays.stream(nanos).anyMatch(n->n<0))throw new IllegalArgumentException("QUALIFICATION_SAMPLES");
        long[] sorted=nanos.clone();Arrays.sort(sorted);
        return new Statistics(sorted.length,percentile(sorted,.5),percentile(sorted,.9),percentile(sorted,.95),percentile(sorted,.99),percentile(sorted,.999),sorted[sorted.length-1]/1e6,
            above(sorted,4),above(sorted,8),above(sorted,16),above(sorted,50),above(sorted,100));
    }
    private static double percentile(long[] sorted,double fraction){return sorted[(int)Math.ceil(sorted.length*fraction)-1]/1e6;}
    private static long above(long[] sorted,int ms){return Arrays.stream(sorted).filter(n->n>ms*1_000_000L).count();}
    public static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t")+"\"";}
    public static String statisticsJson(Statistics s){return "{\"samples\":"+s.samples()+",\"p50Ms\":"+s.p50Ms()+",\"p90Ms\":"+s.p90Ms()+",\"p95Ms\":"+s.p95Ms()+",\"p99Ms\":"+s.p99Ms()+",\"p999Ms\":"+s.p999Ms()+",\"maxMs\":"+s.maxMs()+",\"above4Ms\":"+s.above4Ms()+",\"above8Ms\":"+s.above8Ms()+",\"above16Ms\":"+s.above16Ms()+",\"above50Ms\":"+s.above50Ms()+",\"above100Ms\":"+s.above100Ms()+"}";}
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: java StorageDurabilityQualification.java NEW_OUTPUT_DIRECTORY SAMPLE_COUNT");
        int count=Integer.parseInt(args[1]);if(count<1000||count>100000)throw new IllegalArgumentException("Use 1000..100000 barriers per mode; release qualification targets 10000.");
        Path directory=Path.of(args[0]).toAbsolutePath().normalize();Files.createDirectory(directory);
        String environment="{\"java\":"+quote(System.getProperty("java.runtime.version"))+",\"vendor\":"+quote(System.getProperty("java.vendor"))+",\"os\":"+quote(System.getProperty("os.name")+" "+System.getProperty("os.version")+" "+System.getProperty("os.arch"))+",\"volume\":"+quote(Files.getFileStore(directory).toString())+",\"filesystem\":"+quote(Files.getFileStore(directory).type())+",\"path\":"+quote(directory.toString())+"}";
        Files.writeString(directory.resolve("environment.json"),environment,CREATE_NEW);
        for(boolean allocated:new boolean[]{false,true})for(int bytes:new int[]{130,8320})run(directory,count,bytes,allocated,environment);
    }
    private static void run(Path directory,int count,int bytes,boolean allocated,String environment)throws IOException {
        String mode=(allocated?"preallocated":"growing")+"-"+(bytes==130?"sparse":"group")+"-force-true";
        Path file=directory.resolve(mode+".wal");long[] forces=new long[count],writes=new long[count],total=new long[count];
        long size=Math.multiplyExact((long)count,bytes);long preparation=0;
        try(var channel=FileChannel.open(file,CREATE_NEW,READ,WRITE)){
            if(allocated){long start=System.nanoTime();channel.position(size-1);channel.write(ByteBuffer.wrap(new byte[1]));channel.force(true);preparation=System.nanoTime()-start;channel.position(0);}
            ByteBuffer data=ByteBuffer.allocate(bytes);Arrays.fill(data.array(),(byte)0x5a);
            for(int i=0;i<count;i++){
                data.clear();data.putLong(0,i+1);long start=System.nanoTime();while(data.hasRemaining())channel.write(data);long beforeForce=System.nanoTime();
                channel.force(true);long end=System.nanoTime();writes[i]=beforeForce-start;forces[i]=end-beforeForce;total[i]=end-start;
            }
            if(channel.size()!=size)throw new IOException("QUALIFICATION_EXTENT_MISMATCH");
        }
        String result="{\"mode\":"+quote(mode)+",\"forceMetadata\":true,\"preallocated\":"+allocated+",\"bytesPerBarrier\":"+bytes+",\"fileBytes\":"+size+",\"preparationMs\":"+preparation/1e6+",\"environment\":"+environment+",\"force\":"+statisticsJson(statistics(forces))+",\"write\":"+statisticsJson(statistics(writes))+",\"writeAndForce\":"+statisticsJson(statistics(total))+",\"rawForceNanos\":"+Arrays.toString(forces)+",\"rawWriteNanos\":"+Arrays.toString(writes)+",\"rawTotalNanos\":"+Arrays.toString(total)+",\"discardedSamples\":0,\"connectedProof\":false}";
        Files.writeString(directory.resolve(mode+".json"),result,CREATE_NEW);System.out.println(mode+" "+statisticsJson(statistics(forces)));
        // Retain generated raw files for reproducibility; no implicit cleanup or overwrite.
    }
}
