package com.badlyac.flattrans.data;

import com.badlyac.flattrans.FlatTrans;
import com.badlyac.flattrans.network.TeleporterEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 記錄該維度中所有已放置的傳送裝置位置與名稱。
 * 存在 level 的 SavedData 中，跨區塊卸載與伺服器重啟持久化。
 */
public class TeleporterSavedData extends SavedData {
    private static final String DATA_NAME = FlatTrans.MODID + "_teleporters";

    private static final Factory<TeleporterSavedData> FACTORY =
            new Factory<>(TeleporterSavedData::new, TeleporterSavedData::load);

    private final Map<BlockPos, String> teleporters = new HashMap<>();

    public static TeleporterSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void add(BlockPos pos, String name) {
        teleporters.put(pos.immutable(), name);
        setDirty();
    }

    public void setName(BlockPos pos, String name) {
        if (teleporters.containsKey(pos)) {
            teleporters.put(pos.immutable(), name);
            setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (teleporters.remove(pos) != null) {
            setDirty();
        }
    }

    public boolean contains(BlockPos pos) {
        return teleporters.containsKey(pos);
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
            NbtUtils.readBlockPos(entry, "Pos")
                    .ifPresent(pos -> data.teleporters.put(pos, entry.getString("Name")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        teleporters.forEach((pos, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("Pos", NbtUtils.writeBlockPos(pos));
            entry.putString("Name", name);
            list.add(entry);
        });
        tag.put("Teleporters", list);
        return tag;
    }
}
