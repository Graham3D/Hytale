package com.inigmasgames.hytalerpg.execution.summon;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** CREATE_NEW is the cross-process claim. Even an empty/torn receipt means consumed (fail closed).
 * One small immutable receipt per consumed corpse; no growing in-memory index or per-cast directory scan.
 * Receipts must travel with world backups. Power-loss durability depends on the host filesystem. */
public final class FileCorpseConsumptionStore implements CorpseConsumptionStore {
    private final Path root;
    public FileCorpseConsumptionStore(Path root){this.root=root.toAbsolutePath().normalize();}
    private Path receipt(UUID world,UUID corpse){return root.resolve(world.toString()).resolve(corpse+".consumed");}
    @Override public boolean consumed(UUID world,UUID corpse){
        try{Files.readAttributes(receipt(world,corpse),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);return true;}
        catch(NoSuchFileException absent){return false;}
        catch(IOException failure){throw new UncheckedIOException("CORPSE_RECEIPT_READ_FAILED",failure);}
    }
    @Override public boolean consume(CorpseLedger.Claim claim){
        var path=receipt(claim.source().world(),claim.entity());
        if(claim.root().length()>1024)throw new IllegalArgumentException("CORPSE_ROOT_TOO_LONG");
        try{
            Files.createDirectories(path.getParent());
            try(var channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                var bytes=ByteBuffer.wrap(("v1\n"+claim.owner()+"\n"+claim.nonce()+"\n"+claim.root()+"\n").getBytes(StandardCharsets.UTF_8));
                while(bytes.hasRemaining())channel.write(bytes);
                channel.force(true);
                return true;
            }
        }catch(FileAlreadyExistsException duplicate){return false;}
        catch(IOException failure){throw new UncheckedIOException("CORPSE_RECEIPT_COMMIT_FAILED",failure);}
    }
}
