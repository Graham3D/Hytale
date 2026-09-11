package com.inigmasgames.hytalerpg.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Writer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Diagnostics only. No caller-runs policy, unbounded queue, or gameplay-thread file I/O. */
public final class BoundedTraceWriter implements AutoCloseable {
    public static final int QUEUE_RECORDS=256,MAX_RECORD_CHARS=2_097_152;
    public static final long QUEUE_BYTES=8L*1024*1024;
    private final java.nio.file.Path path;private final long maxBytes;
    private final ThreadPoolExecutor worker;
    private final Gson json=new GsonBuilder().disableHtmlEscaping().create();
    private final AtomicLong accepted=new AtomicLong(),written=new AtomicLong(),dropped=new AtomicLong(),failed=new AtomicLong(),bytes=new AtomicLong();
    private final Consumer<Throwable> failure;private final Sink sink;
    private final TraceArchiveManager.Compression compression;private final boolean verify;
    private volatile TraceSegmentWriter segmentWriter;
    private long reportedDropped,reportedFailed;
    @FunctionalInterface interface Sink {void write(byte[] line)throws Exception;}
    public record Metrics(long accepted,long written,long dropped,long failed,int queuedRecords,long retainedBytes,
                          long activeSegmentBytes,long activeSegmentEvents){}
    public BoundedTraceWriter(java.nio.file.Path path,long maxBytes,int retained,Consumer<Throwable> failure){
        this(path,maxBytes,retained,failure,TraceArchiveManager.Compression.GZIP,true,null);
    }
    public BoundedTraceWriter(java.nio.file.Path path,long maxBytes,int retained,Consumer<Throwable> failure,
                              TraceArchiveManager.Compression compression,boolean verify){
        this(path,maxBytes,retained,failure,compression,verify,null);
    }
    BoundedTraceWriter(java.nio.file.Path path,long maxBytes,int retained,Consumer<Throwable> failure,Sink sink){
        this(path,maxBytes,retained,failure,TraceArchiveManager.Compression.GZIP,true,sink);
    }
    BoundedTraceWriter(java.nio.file.Path path,long maxBytes,int retained,Consumer<Throwable> failure,
                       TraceArchiveManager.Compression compression,boolean verify,Sink sink){
        if(maxBytes<65_536||retained<1||retained>32)throw new IllegalArgumentException("TRACE_RETENTION_LIMIT");
        this.path=path.toAbsolutePath();this.maxBytes=maxBytes;this.failure=failure;this.compression=compression;this.verify=verify;
        this.sink=sink==null?this::writeFile:sink;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(QUEUE_RECORDS),task->{
            var thread=new Thread(task,"rpg-bounded-trace-writer");thread.setDaemon(true);return thread;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    public boolean submit(Object record){
        byte[] line;
        try{var limited=new LimitedWriter();json.toJson(record,limited);line=(limited.value+System.lineSeparator()).getBytes(StandardCharsets.UTF_8);}
        catch(RuntimeException error){drop(error);return false;}
        if(line.length>QUEUE_BYTES){drop(new IllegalArgumentException("TRACE_RECORD_BYTE_LIMIT"));return false;}
        long prior;
        do{prior=bytes.get();if(prior+line.length>QUEUE_BYTES){drop(new IllegalStateException("TRACE_QUEUE_BYTE_LIMIT"));return false;}}
        while(!bytes.compareAndSet(prior,prior+line.length));
        try{
            worker.execute(()->{
                try{writeGap();sink.write(line);written.incrementAndGet();}
                catch(Exception error){failed.incrementAndGet();notifyFailure(error);}
                finally{bytes.addAndGet(-line.length);}
            });
            accepted.incrementAndGet();return true;
        }catch(RejectedExecutionException full){bytes.addAndGet(-line.length);drop(full);return false;}
    }
    private void drop(Throwable error){dropped.incrementAndGet();notifyFailure(error);}
    private void notifyFailure(Throwable error){try{failure.accept(error);}catch(RuntimeException ignored){}}
    private void writeGap()throws Exception{
        long d=dropped.get(),f=failed.get();if(d==reportedDropped&&f==reportedFailed)return;
        var record=Map.of("eventType","TRACE_GAP","timestamp",Instant.now().toString(),"droppedTotal",d,"failedWritesTotal",f,
                "droppedSincePrevious",d-reportedDropped,"failedSincePrevious",f-reportedFailed,"evidenceComplete",false);
        sink.write((json.toJson(record)+System.lineSeparator()).getBytes(StandardCharsets.UTF_8));reportedDropped=d;reportedFailed=f;
    }
    private void writeFile(byte[] line)throws IOException{
        TraceSegmentWriter current=segmentWriter;
        if(current==null){current=new TraceSegmentWriter(path,maxBytes,inferKind(path),compression,verify,this::notifyFailure);segmentWriter=current;}
        current.write(line);
    }
    private static String inferKind(java.nio.file.Path path){return path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).startsWith("ui-")?"UI":"SKILL";}
    public Metrics metrics(){var current=segmentWriter;return new Metrics(accepted.get(),written.get(),dropped.get(),failed.get(),
            worker.getQueue().size(),bytes.get(),current==null?0:current.activeBytes(),current==null?0:current.activeEvents());}
    public TraceArchiveManager.Metrics archiveMetrics(){var current=segmentWriter;return current==null?null:current.archive().metrics();}
    @Override public void close(){
        worker.shutdown();
        boolean drained=false;
        try{
            if(worker.awaitTermination(5,TimeUnit.SECONDS)){
                drained=true;
                try{writeGap();}catch(Exception error){failed.incrementAndGet();notifyFailure(error);}
            }else notifyFailure(new IllegalStateException("TRACE_CLOSE_TIMEOUT_EVIDENCE_INCOMPLETE"));
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();notifyFailure(interrupted);}
        TraceSegmentWriter current=segmentWriter;
        if(drained&&current!=null)try{current.close();}catch(Exception error){failed.incrementAndGet();notifyFailure(error);}
    }
    TraceArchiveManager archiveManager(){return segmentWriter==null?null:segmentWriter.archive();}
    private static final class LimitedWriter extends Writer {
        final StringBuilder value=new StringBuilder();
        @Override public void write(char[] chars,int offset,int length){
            if(value.length()+length>MAX_RECORD_CHARS)throw new IllegalArgumentException("TRACE_RECORD_SIZE_LIMIT");value.append(chars,offset,length);
        }
        @Override public void write(String text,int offset,int length){
            if(value.length()+length>MAX_RECORD_CHARS)throw new IllegalArgumentException("TRACE_RECORD_SIZE_LIMIT");value.append(text,offset,offset+length);
        }
        @Override public void flush(){}@Override public void close(){}
    }
}
