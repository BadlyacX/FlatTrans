package com.badlyac.flattrans.data;

import com.badlyac.flattrans.FlatTrans;
import com.badlyac.flattrans.network.TeleporterEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 記錄伺服器上所有已放置的傳送裝置位置（含維度）與名稱。
 * 不管哪個維度呼叫 {@link #get}，都會取得同一份存在主世界的 SavedData，
 * 讓傳送裝置清單可以跨維度共用，藉此支援跨維度傳送。
 */
public class TeleporterSavedData extends SavedData {
    private static final String DATA_NAME = FlatTrans.MODID + "_teleporters";

    private static final Factory<TeleporterSavedData> FACTORY =
            new Factory<>(TeleporterSavedData::new, TeleporterSavedData::load);

    private final Map<GlobalPos, String> teleporters = new HashMap<>();

    public static TeleporterSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void add(GlobalPos pos, String name) {
        teleporters.put(pos, name);
        setDirty();
    }

    public void setName(GlobalPos pos, String name) {
        if (teleporters.containsKey(pos)) {
            teleporters.put(pos, name);
            setDirty();
        }
    }

    public void remove(GlobalPos pos) {
        if (teleporters.remove(pos) != null) {
            setDirty();
        }
    }

    public boolean contains(GlobalPos pos) {
        return teleporters.containsKey(pos);
    }

    public String getName(GlobalPos pos) {
        return teleporters.getOrDefault(pos, "");
    }

    public List<TeleporterEntry> getEntries() {
        List<TeleporterEntry> entries = new ArrayList<>();
        teleporters.forEach((pos, name) -> entries.add(new TeleporterEntry(pos, name)));
        return entries;
    }

    private static TeleporterSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TeleporterSavedData data = new TeleporterSavedData();
        ListTag list = tag.getList("Teleporters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getString("Dimension")));
            NbtUtils.readBlockPos(entry, "Pos")
                    .ifPresent(pos -> data.teleporters.put(GlobalPos.of(dimension, pos), entry.getString("Name")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        teleporters.forEach((globalPos, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", globalPos.dimension().location().toString());
            entry.put("Pos", NbtUtils.writeBlockPos(globalPos.pos()));
            entry.putString("Name", name);
            list.add(entry);
        });
        tag.put("Teleporters", list);
        return tag;
    }
}
