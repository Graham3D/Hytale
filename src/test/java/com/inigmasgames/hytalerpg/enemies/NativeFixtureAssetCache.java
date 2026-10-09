package com.inigmasgames.hytalerpg.enemies;

/** Native asset classes cache their store beyond AssetRegistry.unregister; isolate offline fixture registries. */
final class NativeFixtureAssetCache {
    private NativeFixtureAssetCache(){}
    static void clear(Class<?> type){
        try{var field=type.getDeclaredField("ASSET_STORE");field.setAccessible(true);field.set(null,null);}
        catch(ReflectiveOperationException failure){throw new AssertionError("Pinned native asset cache changed: "+type.getName(),failure);}
    }
}
