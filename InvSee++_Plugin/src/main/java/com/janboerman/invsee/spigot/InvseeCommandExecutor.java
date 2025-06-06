package com.janboerman.invsee.spigot;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.Exempt;
import com.janboerman.invsee.spigot.api.InvseeAPI;
import com.janboerman.invsee.spigot.api.MainSpectatorInventory;
import com.janboerman.invsee.spigot.api.MainSpectatorInventoryView;
import com.janboerman.invsee.spigot.api.response.*;
import com.janboerman.invsee.spigot.api.target.Target;
/*
import com.janboerman.invsee.spigot.multiverseinventories.MultiverseInventoriesSeeApi;
import com.janboerman.invsee.spigot.multiverseinventories.MviCommandArgs;
 */
import com.janboerman.invsee.spigot.api.template.PlayerInventorySlot;
import com.janboerman.invsee.spigot.perworldinventory.PerWorldInventorySeeApi;
import com.janboerman.invsee.spigot.perworldinventory.PwiCommandArgs;
import com.janboerman.invsee.utils.Either;
import com.janboerman.invsee.utils.StringHelper;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class InvseeCommandExecutor implements CommandExecutor {

    private final InvseePlusPlus plugin;

    public InvseeCommandExecutor(InvseePlusPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        if (!(sender instanceof Player)) {
            sender.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fВы должны быть в §6игре§f, чтобы сделать это.");
            return true;
        }

        Player player = (Player) sender;

        String playerNameOrUUID = args[0];
        boolean isUuid;
        UUID uuid = null;
        try {
            uuid = UUID.fromString(playerNameOrUUID);
            isUuid = true;
        } catch (IllegalArgumentException e) {
            isUuid = false;
        }
        final boolean finalIsUuid = isUuid;

        final InvseeAPI api = plugin.getApi();
        final Target target = isUuid ? Target.byUniqueId(uuid) : Target.byUsername(playerNameOrUUID);
        //TODO why not just: plugin.getInventoryCreationOptions() ?
        final CreationOptions<PlayerInventorySlot> creationOptions = CreationOptions.defaultMainInventory(plugin)
                .withTitle(plugin.getTitleForInventory())
                .withMirror(plugin.getInventoryMirror())
                .withOfflinePlayerSupport(plugin.offlinePlayerSupport())
                .withUnknownPlayerSupport(plugin.unknownPlayerSupport())
                .withBypassExemptedPlayers(player.hasPermission(Exempt.BYPASS_EXEMPT_INVENTORY))
                .withLogOptions(plugin.getLogOptions())
                .withPlaceholderPalette(plugin.getPlaceholderPalette());

        CompletableFuture<SpectateResponse<MainSpectatorInventory>> pwiFuture = null;

        if (args.length > 1 && api instanceof PerWorldInventorySeeApi) {
            String pwiArgument = StringHelper.joinArray(" ", 1, args);
            PerWorldInventorySeeApi pwiApi = (PerWorldInventorySeeApi) api;

            Either<String, PwiCommandArgs> either = PwiCommandArgs.parse(pwiArgument, pwiApi.getHook());
            if (either.isLeft()) {
                player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §f" + either.getLeft());
                return true;
            }

            PwiCommandArgs pwiOptions = either.getRight();
            CompletableFuture<Optional<UUID>> uuidFuture = isUuid
                    ? CompletableFuture.completedFuture(Optional.of(uuid))
                    : pwiApi.fetchUniqueId(playerNameOrUUID);

            pwiFuture = uuidFuture.thenCompose(optId -> {
                if (optId.isPresent()) {
                    UUID uniqueId = optId.get();
                    com.janboerman.invsee.spigot.perworldinventory.ProfileId profileId
                            = new com.janboerman.invsee.spigot.perworldinventory.ProfileId(pwiApi.getHook(), pwiOptions, uniqueId);
                    CompletableFuture<String> userNameFuture = finalIsUuid
                            ? api.fetchUserName(uniqueId).thenApply(o -> o.orElse("InvSee++ Player")).exceptionally(t -> "InvSee++ Player")
                            : CompletableFuture.completedFuture(playerNameOrUUID);
                    return userNameFuture.thenCompose(playerName -> pwiApi.spectateInventory(uniqueId, playerName, creationOptions, profileId));
                } else {
                    return CompletableFuture.completedFuture(SpectateResponse.fail(NotCreatedReason.targetDoesNotExists(target)));
                }
            });
        }

        /*
        else if (args.length > 1 && api instanceof MultiverseInventoriesSeeApi) {
            String mviArgument = StringHelper.joinArray(" ", 1, args);
            MultiverseInventoriesSeeApi mviApi = (MultiverseInventoriesSeeApi) api;

            Either<String, MviCommandArgs> either = MviCommandArgs.parse(mviArgument, mviApi.getHook());
            if (either.isLeft()) {
                player.sendMessage(ChatColor.RED + either.getLeft());
                return true;
            }

            MviCommandArgs mviOptions = either.getRight();
            CompletableFuture<Optional<UUID>> uuidFuture = isUuid
                    ? CompletableFuture.completedFuture(Optional.of(uuid))
                    : mviApi.fetchUniqueId(playerNameOrUUID);

            final boolean finalIsUuid = isUuid;
            future = uuidFuture.thenCompose(optId -> {
                if (optId.isPresent()) {
                    UUID uniqueId = optId.get();
                    CompletableFuture<String> usernameFuture = finalIsUuid
                            ? api.fetchUserName(uniqueId).thenApply(o -> o.orElse("InvSee++ Player")).exceptionally(t -> "InvSee++ Player")
                            : CompletableFuture.completedFuture(playerNameOrUUID);
                    return usernameFuture.thenCompose(playerName -> mviApi.spectateInventory(uniqueId, playerName, title,
                            new com.janboerman.invsee.spigot.multiverseinventories.ProfileId(mviApi.getHook(), mviOptions, uniqueId, playerName)));
                } else {
                    return CompletableFuture.completedFuture(SpectateResponse.fail(NotCreatedReason.targetDoesNotExists(Target.byUsername(playerNameOrUUID))));
                }
            });
        }
         */

        CompletableFuture<OpenResponse<MainSpectatorInventoryView>> fut;

        if (pwiFuture != null) {
            //PWI future is not null - open the inventory!
            fut = pwiFuture.thenApply(response -> response.isSuccess()
                        ? ((PerWorldInventorySeeApi) api).openMainSpectatorInventory(player, response.getInventory(), creationOptions)
                        : OpenResponse.closed(NotOpenedReason.notCreated(response.getReason())));
        }

        //TODO else if (mviFuture != null) { ... }

        else {
            //No PWI argument - just continue with the regular method
            if (isUuid) {
                //playerNameOrUUID is a UUID.
                final UUID finalUuid = uuid;

                //convert UUID to username, then spectate the inventory!
                fut = api.fetchUserName(uuid).thenApply(o -> o.orElse("InvSee++ Player")).exceptionally(t -> "InvSee++ Player")
                        .thenCompose(userName -> api.spectateInventory(player, finalUuid, userName, creationOptions));
            } else {
                //spectate the target's inventory!
                fut = api.spectateInventory(player, playerNameOrUUID, creationOptions);
            }
        }

        //Gracefully handle failure and faults.
        fut.whenComplete((response, throwable) -> {
            if (throwable != null) {
                player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fПроизошла §6ошибка§f при открытии инвентаря §7" + playerNameOrUUID + "§f.");
                plugin.getLogger().log(Level.SEVERE, "§8[§cx§8]§r §9Система §8» §fОшибка при создании §6инвентаря§f для просмотра", throwable);
            } else {
                if (!response.isOpen()) {
                    NotOpenedReason notOpenedReason = response.getReason();
                    if (notOpenedReason instanceof InventoryOpenEventCancelled) {
                        player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fДругой плагин §6заблокировал§f просмотр инвентаря §7" + playerNameOrUUID + "§f.");
                    } else if (notOpenedReason instanceof InventoryNotCreated) {
                        NotCreatedReason notCreatedReason = ((InventoryNotCreated) notOpenedReason).getNotCreatedReason();
                        if (notCreatedReason instanceof TargetDoesNotExist) {
                            player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fИгрока §7" + playerNameOrUUID + " §6не существует§f.");
                        } else if (notCreatedReason instanceof UnknownTarget) {
                            player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fИгрок §7" + playerNameOrUUID + " §fещё не §6заходил на сервер§f.");
                        } else if (notCreatedReason instanceof TargetHasExemptPermission) {
                            player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fИгрок §7" + playerNameOrUUID + " §fимеет §6защиту§f от просмотра инвентаря.");
                        } else if (notCreatedReason instanceof ImplementationFault) {
                            player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fОшибка при §6загрузке инвентаря§f игрока §7" + playerNameOrUUID + "§f.");
                        } else if (notCreatedReason instanceof OfflineSupportDisabled) {
                            player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fПросмотр инвентарей §6оффлайн-игроков §fотключён.");
                        } else {
                            player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fНе удалось §6создать инвентарь §7" + playerNameOrUUID + " §fпо неизвестной причине.");
                        }
                    } else {
                        player.sendMessage("§8[§cx§8]§r §9Инвентарь §8» §fНе удалось открыть инвентарь §7" + playerNameOrUUID + " §fпо неизвестной причине.");
                    }
                } //else: it opened successfully: nothing to do there!
            }
        });

        return true;
    }

}
