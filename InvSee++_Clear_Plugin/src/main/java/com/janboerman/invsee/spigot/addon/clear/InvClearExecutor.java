package com.janboerman.invsee.spigot.addon.clear;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.InvseeAPI;
import com.janboerman.invsee.spigot.api.MainSpectatorInventory;
import com.janboerman.invsee.spigot.api.response.*;
import com.janboerman.invsee.spigot.api.template.PlayerInventorySlot;
import com.janboerman.invsee.utils.Either;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

class InvClearExecutor implements CommandExecutor {

    private final ClearPlugin plugin;
    private final InvseeAPI api;

    InvClearExecutor(ClearPlugin plugin, InvseeAPI api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;

        String inputPlayer = args[0];

        Either<UUID, String> eitherPlayer = Convert.convertPlayer(inputPlayer);
        CompletableFuture<Optional<UUID>> uuidFuture;
        CompletableFuture<Optional<String>> userNameFuture;
        if (eitherPlayer.isLeft()) {
            UUID uuid = eitherPlayer.getLeft();
            uuidFuture = CompletableFuture.completedFuture(Optional.of(uuid));
            userNameFuture = api.fetchUserName(uuid);
        } else {
            assert eitherPlayer.isRight();
            String userName = eitherPlayer.getRight();
            userNameFuture = CompletableFuture.completedFuture(Optional.of(userName));
            uuidFuture = api.fetchUniqueId(userName);
        }

        ItemType itemType = null;
        //TODO nbt tag?
        //TODO components? This is actually possible now!

        int maxCount = -1;

        if (args.length >= 2) {
            String inputItemType = args[1];
            Either<String, ItemType> eitherMaterial = Convert.convertItemType(inputItemType);
            if (eitherMaterial.isRight()) {
                itemType = eitherMaterial.getRight();
            } else {
                assert eitherMaterial.isLeft();
                sender.sendMessage("§c§l✖§r §9Инвентарь §8» §f" + eitherMaterial.getLeft());
                return true;
            }
        }

        if (args.length >= 3) {
            String inputMaxCount = args[2];
            Either<String, Integer> eitherMaxCount = Convert.convertAmount(inputMaxCount);
            if (eitherMaxCount.isRight()) {
                maxCount = eitherMaxCount.getRight();
            } else {
                assert eitherMaxCount.isLeft();
                sender.sendMessage("§c§l✖§r §9Инвентарь §8» §f" + eitherMaxCount.getLeft());
                return true;
            }
        }

        final ItemType finalItemType = itemType;
        final int finalMaxCount = maxCount;
        final CreationOptions<PlayerInventorySlot> creationOptions = api.mainInventoryCreationOptions()
                .withOfflinePlayerSupport(plugin.offlinePlayerSupport())
                .withBypassExemptedPlayers(plugin.bypassExemptInvsee(sender));

        uuidFuture.<Optional<String>, Void>thenCombineAsync(userNameFuture, (optUuid, optName) -> {
            if (!optName.isPresent() || !optUuid.isPresent()) {
                sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрока §7" + inputPlayer + " §6не существует§f.");
            } else {
                String userName = optName.get();
                UUID uuid = optUuid.get();

                CompletableFuture<SpectateResponse<MainSpectatorInventory>> responseFuture
                        = api.mainSpectatorInventory(uuid, userName, creationOptions);
                responseFuture.thenAcceptAsync(response -> {
                    if (response.isSuccess()) {
                        MainSpectatorInventory inventory = response.getInventory();
                        if (finalItemType == null) {
                            inventory.clear();
                            sender.sendMessage("§a§l✔§r §9Инвентарь §8» §fИнвентарь игрока §7" + userName + " §6очищен§f.");
                        } else {
                            if (finalMaxCount == -1) {
                                finalItemType.removeAllFrom(inventory);
                                sender.sendMessage("§a§l✔§r §9Инвентарь §8» §fУдалены все предметы §e" + finalItemType + " §fиз инвентаря §7" + userName + "§f.");
                            } else {
                                int removed = finalItemType.removeAtMostFrom(inventory, finalMaxCount);
                                sender.sendMessage("§a§l✔§r §9Инвентарь §8» §fУдалено §e" + removed + " " + finalItemType + " §fиз инвентаря §7" + userName + "§f.");
                            }
                        }
                        api.saveInventory(inventory).whenComplete((v, e) -> {
                            if (e != null) plugin.getLogger().log(Level.SEVERE, "§c§l✖§r §9Система §8» §fНе удалось сохранить §6инвентарь§f", e);
                        });
                    } else {
                        NotCreatedReason reason = response.getReason();
                        if (reason instanceof TargetDoesNotExist) {
                            TargetDoesNotExist targetDoesNotExist = (TargetDoesNotExist) reason;
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрока §7" + targetDoesNotExist.getTarget() + " §6не существует§f.");
                        } else if (reason instanceof UnknownTarget) {
                            UnknownTarget unknownTarget = (UnknownTarget) reason;
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрок §7" + unknownTarget.getTarget() + " §fещё не §6заходил на сервер§f.");
                        } else if (reason instanceof TargetHasExemptPermission) {
                            TargetHasExemptPermission targetHasExemptPermission = (TargetHasExemptPermission) reason;
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрок §7" + targetHasExemptPermission.getTarget() + " §fимеет §6защиту§f от просмотра инвентаря.");
                        } else if (reason instanceof ImplementationFault) {
                            ImplementationFault implementationFault = (ImplementationFault) reason;
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fОшибка при §6загрузке инвентаря§f игрока §7" + implementationFault.getTarget() + "§f.");
                        } else if (reason instanceof OfflineSupportDisabled) {
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fПросмотр инвентарей §6оффлайн-игроков §fотключён.");
                        } else {
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fНе удалось очистить инвентарь §7" + inputPlayer + " §fпо неизвестной причине.");
                        }
                    }
                }, runnable -> api.getScheduler().executeSyncPlayer(uuid, runnable, null));
            }

            return null;
        }, api.getScheduler()::executeSyncGlobal);
        return true;
    }

}
