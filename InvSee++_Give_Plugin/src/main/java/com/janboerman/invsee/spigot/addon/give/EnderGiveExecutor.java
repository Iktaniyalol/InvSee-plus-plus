package com.janboerman.invsee.spigot.addon.give;

import com.janboerman.invsee.spigot.addon.give.cmd.ArgParser;
import com.janboerman.invsee.spigot.addon.give.cmd.ArgType;
import com.janboerman.invsee.spigot.addon.give.common.GiveApi;
import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.EnderSpectatorInventory;
import com.janboerman.invsee.spigot.api.InvseeAPI;
import com.janboerman.invsee.spigot.api.response.ImplementationFault;
import com.janboerman.invsee.spigot.api.response.NotCreatedReason;
import com.janboerman.invsee.spigot.api.response.OfflineSupportDisabled;
import com.janboerman.invsee.spigot.api.response.SpectateResponse;
import com.janboerman.invsee.spigot.api.response.TargetDoesNotExist;
import com.janboerman.invsee.spigot.api.response.TargetHasExemptPermission;
import com.janboerman.invsee.spigot.api.response.UnknownTarget;
import com.janboerman.invsee.spigot.api.template.EnderChestSlot;
import com.janboerman.invsee.utils.Either;
import com.janboerman.invsee.utils.Pair;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

class EnderGiveExecutor implements CommandExecutor {

    private final GivePlugin plugin;
    private final InvseeAPI invseeApi;
    private final GiveApi giveApi;
    private final ItemQueueManager queueManager;

    EnderGiveExecutor(GivePlugin plugin, InvseeAPI invseeApi, GiveApi giveApi, ItemQueueManager queueManager) {
        this.plugin = plugin;
        this.invseeApi = invseeApi;
        this.giveApi = giveApi;
        this.queueManager = queueManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length < 2) return false;

        Optional<List<ArgType>> maybeFormat = ArgParser.determineFormat(args);
        if (!maybeFormat.isPresent()) {
            // Could not recognise the format. Exit early and let bukkit print the usage string.
            return false;
        }

        List<ArgType> format = maybeFormat.get();
        Map<ArgType, String> groupedArguments = ArgParser.splitArguments(format, args);

        String inputPlayer = groupedArguments.get(ArgType.TARGET);
        Pair<CompletableFuture<Optional<UUID>>, CompletableFuture<Optional<String>>> futures = ArgParser.parseTarget(invseeApi, inputPlayer);
        CompletableFuture<Optional<UUID>> uuidFuture = futures.getFirst();
        CompletableFuture<Optional<String>> userNameFuture = futures.getSecond();

        Either<String, ItemStack> eitherStack = ArgParser.parseItem(giveApi, groupedArguments);
        if (eitherStack.isLeft()) {
            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §f" + eitherStack.getLeft());
            return true;
        }
        assert eitherStack.isRight();

        final ItemStack finalItems = eitherStack.getRight();
        final CreationOptions<EnderChestSlot> creationOptions = invseeApi.enderInventoryCreationOptions()
                .withOfflinePlayerSupport(plugin.offlinePlayerSupport())
                .withUnknownPlayerSupport(plugin.unknownPlayerSupport())
                .withBypassExemptedPlayers(plugin.bypassExemptEndersee(sender));

        uuidFuture.<Optional<String>, Void>thenCombineAsync(userNameFuture, (optUuid, optName) -> {
            if (!optName.isPresent() || !optUuid.isPresent()) {
                sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрока §7" + inputPlayer + " §6не существует§f.");
            } else {
                String userName = optName.get();
                UUID uuid = optUuid.get();

                CompletableFuture<SpectateResponse<EnderSpectatorInventory>> responseFuture =
                        invseeApi.enderSpectatorInventory(uuid, userName, creationOptions);
                responseFuture.thenAcceptAsync(response -> {
                    if (response.isSuccess()) {
                        EnderSpectatorInventory inventory = response.getInventory();
                        final ItemStack originalItems = finalItems.clone();
                        Map<Integer, ItemStack> map = inventory.addItem(new ItemStack[] { finalItems });
                        if (map.isEmpty()) {
                            //success!!
                            if (plugin.getServer().getPlayer(uuid) == null)
                                //if the player is offline, save the inventory.
                                invseeApi.saveEnderChest(inventory).whenComplete((v, e) -> {
                                    if (e != null) plugin.getLogger().log(Level.SEVERE, "§c§l✖§r §9Система §8» §fНе удалось сохранить §6эндер-сундук§f", e);
                                });
                            sender.sendMessage("§a§l✔§r §9Инвентарь §8» §fУспешно добавлено §e" + originalItems + " §fв эндер-сундук §7" + userName + "§f!");
                        } else {
                            //no success. for all the un-merged items, find an item in the player's inventory, and just exceed the material's max stack size!
                            int remainder = map.get(0).getAmount();

                            finalItems.setAmount(remainder);
                            if (plugin.queueRemainingItems()) {
                                sender.sendMessage("§e§l⚡§r §9Инвентарь §8» §fНе удалось добавить §e" + finalItems + " §fв эндер-сундук, §6добавляю в очередь§f...");
                                queueManager.enqueueEnderchest(uuid, plugin.savePartialInventories() ? finalItems : originalItems);
                            } else {
                                sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fНе удалось добавить §e" + finalItems + " §fв эндер-сундук §7" + userName + "§f.");
                            }

                            if (plugin.getServer().getPlayer(uuid) == null && plugin.savePartialInventories())
                                invseeApi.saveEnderChest(inventory).whenComplete((v, e) -> {
                                    if (e != null) plugin.getLogger().log(Level.SEVERE, "§c§l✖§r §9Система §8» §fНе удалось сохранить §6эндер-сундук§f", e);
                                });
                        }
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
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрок §7" + targetHasExemptPermission.getTarget() + " §fимеет §6защиту§f от просмотра эндер-сундука.");
                        } else if (reason instanceof ImplementationFault) {
                            ImplementationFault implementationFault = (ImplementationFault) reason;
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fОшибка при §6загрузке эндер-сундука§f игрока §7" + implementationFault.getTarget() + "§f.");
                        } else if (reason instanceof OfflineSupportDisabled) {
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fПросмотр эндер-сундуков §6оффлайн-игроков §fотключён.");
                        } else {
                            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fНе удалось добавить предметы в эндер-сундук §7" + inputPlayer + " §fпо неизвестной причине.");
                        }
                    }
                }, runnable -> invseeApi.getScheduler().executeSyncPlayer(uuid, runnable, null));
            }

            return null;
        }, invseeApi.getScheduler()::executeSyncGlobal);

        return true;
    }

}
