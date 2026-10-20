package com.inigmasgames.hytalerpg.combat.defense;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefenseViewTest {
    @Test void preservesManagedArmorAtAllApprovedLevelsAndQuantizationBoundaries() {
        for (int level : new int[]{1, 50, 99}) {
            for (double p : new double[]{0, .10, .30, .60}) {
                var view = DefenseView.managed(level, p, DefenseView.Contributions.NONE);
                assertEquals(p, view.managedProtection(), 1e-12);
                assertEquals(Math.round(p * 1000), view.nativeProtectionTenth());
            }
            for (int tenth=0; tenth<=600; tenth++)
                assertEquals(tenth, DefenseView.managed(level,tenth/1000.,DefenseView.Contributions.NONE).nativeProtectionTenth());
            for (int tenth=0;tenth<600;tenth++) {
                double p=(tenth+.5)/1000;
                assertEquals(Math.round(p*1000),DefenseView.managed(level,p,DefenseView.Contributions.NONE).nativeProtectionTenth());
            }
        }
        assertEquals(110, DefenseView.scale(-1));
        assertEquals(1090, DefenseView.scale(1000));
    }
    @Test void globalIncreasedFollowsAllLocalSourcesAndBreakUsesRating() {
        var view=DefenseView.managed(10, 0, new DefenseView.Contributions(100, 100, .5, .25));
        assertEquals(300,view.totalRating());
        assertEquals(225,view.effectiveRating());
        assertEquals(225./425,view.managedProtection(),1e-12);
        var broken=DefenseView.managed(10,0,new DefenseView.Contributions(0,200,0,.25));
        assertEquals(150,broken.effectiveRating());
        assertEquals(150./350,broken.managedProtection(),1e-12);
        assertEquals(75,DefenseView.managed(10,0,new DefenseView.Contributions(0,100,0,.25)).effectiveRating());
    }
    @Test void capDoesNotDiscardDiagnosticRatingAndZeroRatingRemainsZero() {
        var view=DefenseView.managed(1,.6,new DefenseView.Contributions(0,0,.8,0));
        assertEquals(297,view.totalRating(),1e-10);
        assertTrue(view.uncappedProtection()>.6);
        assertEquals(.6,view.managedProtection());
        assertEquals(0,DefenseView.managed(1,0,new DefenseView.Contributions(0,0,.8,.25)).effectiveRating());
        assertEquals(.6,DefenseView.managed(1,1,DefenseView.Contributions.NONE).managedProtection(),1e-12);
    }
    @Test void nativeCauseBaselineIsNotClippedToManagedArmorCap() {
        assertEquals(.8,DefenseView.resolve(50,.8,DefenseView.Contributions.NONE,1).managedProtection(),1e-12);
        assertThrows(IllegalArgumentException.class,()->DefenseView.rating(50,1));
    }
    @Test void rejectsNonfiniteAndInvalidProviderInputs() {
        assertThrows(IllegalArgumentException.class,()->new DefenseView.Contributions(0,0,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->new DefenseView.Contributions(0,0,0,1.01));
        assertThrows(IllegalArgumentException.class,()->DefenseView.rating(1,-.1));
    }
}
