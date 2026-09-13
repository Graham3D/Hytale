package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** Reusable bounded per-segment presentation state. No gameplay or native entities. */
public final class HealingWorldParticleFrame {
    public static final int MAX_BODY=64,PULSES=3;
    public static final double SPACING=.325,PERIOD=.1,PULSE_SPEED=6;
    public static final float LIFETIME=.18f;
    private final ElasticBeamTether motion=new ElasticBeamTether();
    private final Vec3[] samples=new Vec3[MAX_BODY+PULSES];
    private final double[] arcs=new double[PULSES];
    private double next=Double.NEGATIVE_INFINITY,last=Double.NaN;
    private int body,count;
    public int bodyCount(){return body;}
    public int count(){return count;}
    public Vec3 sample(int index){if(index<0||index>=count)throw new IndexOutOfBoundsException(index);return samples[index];}
    public double pulseArc(int index){return arcs[index];}
    void inheritCadence(HealingWorldParticleFrame prior){next=prior.next;}
    public boolean update(Vec3 start,Vec3 end,double now){
        if(!Double.isFinite(now))throw new IllegalArgumentException("HEAL_PACKET_TIME");
        // No catch-up bursts, including after a long stall or a backward clock.
        if(now<next)return false;
        next=now+PERIOD;
        var path=new HealingParticlePath(motion.update(start,end,now));
        int previous=count;
        body=path.length()<1e-6?0:Math.min(MAX_BODY,Math.max(2,(int)Math.ceil(path.length()/SPACING)+1));
        count=body==0?0:body+PULSES;
        for(int i=0;i<body;i++)samples[i]=path.at(path.length()*i/(body-1));
        for(int i=0;i<PULSES;i++){
            if(body==0){arcs[i]=0;continue;}
            arcs[i]=Double.isNaN(last)?path.length()*i/PULSES:(arcs[i]+PULSE_SPEED*(now-last))%path.length();
            samples[body+i]=path.at(arcs[i]);
        }
        for(int i=count;i<previous;i++)samples[i]=null;
        last=now;return true;
    }
}
