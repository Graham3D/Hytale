package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage10NativeMetadataTest {
    @Test void copyingConversionMetadataCannotRearmCompletedRestoration(){
        var original=new ConversionProjection();original.restored=true;original.nextQuery=123;
        var copy=original.clone();assertNotSame(original,copy);assertTrue(copy.restored);assertEquals(123,copy.nextQuery);
    }
}
