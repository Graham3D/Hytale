package com.inigmasgames.hytalerpg.enemies;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Objects;

/** V1 v2-planner stream: SHA-256(length-prefixed UTF-8 domain, seed, big-endian counter).
 * Rejection sampling masks unused high bits; no modulo bias and no shared/legacy RNG state. */
public final class Me12Random {
    public static final String TIER="me12.tier-pair/v1", NAME="me12.name-pair/v1", SET="me12.affix-set/v1";
    private final byte[] prefix;
    private final MessageDigest digest;
    private long counter;
    public Me12Random(String domain,String seed) {
        Objects.requireNonNull(seed);
        if(!java.util.Set.of(TIER,NAME,SET).contains(domain)||seed.isEmpty()||seed.length()>512)throw new IllegalArgumentException("ME12_RNG_DOMAIN_OR_SEED");
        byte[] a=domain.getBytes(StandardCharsets.UTF_8), b=seed.getBytes(StandardCharsets.UTF_8);
        prefix=ByteBuffer.allocate(8+a.length+b.length).putInt(a.length).put(a).putInt(b.length).put(b).array();
        try {digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    public BigInteger uniformBelow(BigInteger bound) {
        if(bound.signum()<=0||bound.bitLength()>4096)throw new IllegalArgumentException("ME12_RNG_BOUND");
        if(bound.equals(BigInteger.ONE))return BigInteger.ZERO;
        int bits=bound.subtract(BigInteger.ONE).bitLength(),size=(bits+7)/8;
        for(int attempts=0;attempts<1024;attempts++) {
            byte[] bytes=new byte[size];
            for(int offset=0;offset<size;) {
                if(counter==Long.MAX_VALUE)throw new IllegalStateException("ME12_RNG_COUNTER_EXHAUSTED");
                digest.update(prefix);byte[] block=digest.digest(ByteBuffer.allocate(8).putLong(counter++).array());
                int n=Math.min(block.length,size-offset);System.arraycopy(block,0,bytes,offset,n);offset+=n;
            }
            bytes[0]&=(byte)(255 >>> (size*8-bits));
            var value=new BigInteger(1,bytes);if(value.compareTo(bound)<0)return value;
        }
        throw new IllegalStateException("ME12_RNG_REJECTION_WATCHDOG");
    }
}
