package com.inigmasgames.hytalerpg.spawning;

import java.util.*;

/** Member-count targets applied to native per-role expected NPCs before native job selection. */
public final class PopulationWeightPlan {
    public record Row(int roleIndex,NativePopulationRoles.Category category,double nativeWeight,int actual,
                      int minimumFlock,boolean eligible) {
        public Row {
            if(roleIndex<0||category==null||!Double.isFinite(nativeWeight)||nativeWeight<0||actual<0||minimumFlock<1)
                throw new IllegalArgumentException("POPULATION_WEIGHT_ROW");
        }
    }
    public record Result(Map<Integer,Double> expected,double baseHostile,double baseWildlife,
                         double targetHostile,double targetWildlife,int actualHostile,int actualWildlife,
                         int actualAvian,int actualOther,boolean adjusted,String reason) {
        public Result{expected=Map.copyOf(expected);}
    }
    private PopulationWeightPlan(){}
    public static Result calculate(double environmentExpected,List<Row> rows,double hostileShare,double wildlifeShare){
        if(!Double.isFinite(environmentExpected)||environmentExpected<0||!Double.isFinite(hostileShare)
                ||!Double.isFinite(wildlifeShare)||hostileShare<=0||wildlifeShare<=0
                ||Math.abs(hostileShare+wildlifeShare-1)>1e-9)
            throw new IllegalArgumentException("POPULATION_BALANCE_TARGET");
        double totalWeight=rows.stream().mapToDouble(Row::nativeWeight).sum();
        var base=new TreeMap<Integer,Double>();
        double hostileWeight=0,wildlifeWeight=0,hostileBase=0,wildlifeBase=0;
        int actualHostile=0,actualWildlife=0,actualAvian=0,actualOther=0;
        for(var row:rows){
            double expected=totalWeight>0?environmentExpected*row.nativeWeight()/totalWeight:0;
            if(base.put(row.roleIndex(),expected)!=null)throw new IllegalArgumentException("DUPLICATE_NATIVE_ROLE_INDEX");
            switch(row.category()){
                case HOSTILE->{actualHostile+=row.actual();if(row.eligible()&&row.nativeWeight()>0){hostileWeight+=row.nativeWeight();hostileBase+=expected;}}
                case WILDLIFE->{actualWildlife+=row.actual();if(row.eligible()&&row.nativeWeight()>0){wildlifeWeight+=row.nativeWeight();wildlifeBase+=expected;}}
                case AMBIENT_AVIAN->actualAvian+=row.actual();
                case OTHER->actualOther+=row.actual();
            }
        }
        if(hostileWeight<=0||wildlifeWeight<=0)
            return new Result(base,hostileBase,wildlifeBase,hostileBase,wildlifeBase,
                    actualHostile,actualWildlife,actualAvian,actualOther,false,"ONLY_ONE_OR_NO_CONTROLLED_CATEGORY");
        double budget=hostileBase+wildlifeBase;
        double hostileTarget=budget*hostileShare,wildlifeTarget=budget*wildlifeShare;
        if(hostileTarget/hostileBase>8||wildlifeTarget/wildlifeBase>8)
            return new Result(base,hostileBase,wildlifeBase,hostileBase,wildlifeBase,
                    actualHostile,actualWildlife,actualAvian,actualOther,false,"AMPLIFICATION_LIMIT");
        var adjusted=new TreeMap<>(base);
        for(var row:rows)if(row.eligible()&&row.nativeWeight()>0){
            if(row.category()==NativePopulationRoles.Category.HOSTILE)
                adjusted.put(row.roleIndex(),hostileTarget*row.nativeWeight()/hostileWeight);
            else if(row.category()==NativePopulationRoles.Category.WILDLIFE)
                adjusted.put(row.roleIndex(),wildlifeTarget*row.nativeWeight()/wildlifeWeight);
        }
        return new Result(adjusted,hostileBase,wildlifeBase,hostileTarget,wildlifeTarget,
                actualHostile,actualWildlife,actualAvian,actualOther,true,"NATIVE_EXPECTED_MEMBER_WEIGHTS");
    }
}
