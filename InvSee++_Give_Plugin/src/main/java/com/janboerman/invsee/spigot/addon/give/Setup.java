package com.janboerman.invsee.spigot.addon.give;

import com.janboerman.invsee.spigot.addon.give.common.GiveApi;
import com.janboerman.invsee.spigot.internal.version.*;
import org.bukkit.Server;

interface Setup {

    GiveApi getGiveApi();

    static Setup setup(GivePlugin plugin) {
        Server server = plugin.getServer();
        ServerSoftware serverSoftware = ServerSoftware.detect(server);

        if (serverSoftware == null)
            throw new RuntimeException(SupportedServerSoftware.getUnsupportedPlatformMessage(server));

        SetupProvider provider = SetupImpl.SUPPORTED.getImplementationProvider(serverSoftware);

        if (provider == null)
            throw new RuntimeException(SetupImpl.SUPPORTED.getUnsupportedVersionMessage(serverSoftware.getPlatform(), server));

        return provider.provide();
    }
}

interface SetupProvider {
    public Setup provide();
}

class SetupImpl implements Setup {

    static SupportedServerSoftware<SetupProvider> SUPPORTED = new SupportedServerSoftware<>();
    static {
        SUPPORTED.registerSupportedVersion(() -> new Impl_1_21_8(), ServerSoftware.CRAFTBUKKIT_1_21_8, new ServerSoftware(MinecraftPlatform.PAPER, MinecraftVersion._1_21_8));
    }

    private final GiveApi api;

    SetupImpl(GiveApi api) {
        this.api = api;
    }

    @Override
    public GiveApi getGiveApi() {
        return api;
    }
}

class Impl_1_21_8 extends SetupImpl {
    Impl_1_21_8() {
        super(com.janboerman.invsee.spigot.addon.give.impl_1_21_8_R5.GiveImpl.INSTANCE);
    }
}
