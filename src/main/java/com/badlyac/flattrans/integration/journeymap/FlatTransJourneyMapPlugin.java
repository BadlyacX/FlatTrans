package com.badlyac.flattrans.integration.journeymap;

import com.badlyac.flattrans.FlatTrans;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;

/**
 * 由 JourneyMap 透過 {@link JourneyMapPlugin} 標註自動掃描並載入，僅在 JourneyMap 存在時才會被實例化。
 * 本模組其他程式碼不會直接參照這個類別，避免在沒有安裝 JourneyMap 時載入到 journeymap.* 的類別而出錯。
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class FlatTransJourneyMapPlugin implements IServerPlugin {
    @Override
    public void initialize(IServerAPI jmServerApi) {
        JourneyMapIntegration.setServerApi(jmServerApi);
    }

    @Override
    public String getModId() {
        return FlatTrans.MODID;
    }
}
