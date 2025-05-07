package com.janboerman.invsee.spigot;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.EnderSpectatorInventory;
import com.janboerman.invsee.spigot.api.EnderSpectatorInventoryView;
import com.janboerman.invsee.spigot.api.Exempt;
import com.janboerman.invsee.spigot.api.InvseeAPI;
import com.janboerman.invsee.spigot.api.response.ImplementationFault;
import com.janboerman.invsee.spigot.api.response.InventoryNotCreated;
import com.janboerman.invsee.spigot.api.response.InventoryOpenEventCancelled;
import com.janboerman.invsee.spigot.api.response.NotCreatedReason;
import com.janboerman.invsee.spigot.api.response.NotOpenedReason;
import com.janboerman.invsee.spigot.api.response.OfflineSupportDisabled;
import com.janboerman.invsee.spigot.api.response.OpenResponse;
import com.janboerman.invsee.spigot.api.response.SpectateResponse;
import com.janboerman.invsee.spigot.api.response.TargetDoesNotExist;
import com.janboerman.invsee.spigot.api.response.TargetHasExemptPermission;
import com.janboerman.invsee.spigot.api.response.UnknownTarget;
import com.janboerman.invsee.spigot.api.target.Target;
import com.janboerman.invsee.spigot.api.template.EnderChestSlot;
import com.janboerman.invsee.spigot.perworldinventory.PerWorldInventorySeeApi;
import com.janboerman.invsee.spigot.perworldinventory.ProfileId;
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

public class EnderseeCommandExecutor implements CommandExecutor {

    private final InvseePlusPlus plugin;

    public EnderseeCommandExecutor(InvseePlusPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§l✖§r §9Инвентарь §8» §fВы должны быть в §6игре§f, чтобы сделать это.");
            return true;
        }

        Player player = (Player) sender;

        String playerNameOrUUID = args[0];
        UUID uuid;
        boolean isUuid;
        try {
            uuid = UUID.fromString(playerNameOrUUID);
            isUuid = true;
        } catch (IllegalArgumentException e) {
            isUuid = false;
            uuid = null;
        }

        final InvseeAPI api = plugin.getApi();
        final Target target = isUuid ? Target.byUniqueId(uuid) : Target.byUsername(playerNameOrUUID);
        //TODO why not just: plugin.getEnderChestCreationOptions() ?
        final CreationOptions<EnderChestSlot> creationOptions = CreationOptions.defaultEnderInventory(plugin)
                .withTitle(plugin.getTitleForEnderChest())
                .withMirror(plugin.getEnderChestMirror())
                .withOfflinePlayerSupport(plugin.offlinePlayerSupport())
                .withUnknownPlayerSupport(plugin.unknownPlayerSupport())
                .withBypassExemptedPlayers(player.hasPermission(Exempt.BYPASS_EXEMPT_ENDERCHEST))
                .withLogOptions(plugin.getLogOptions())
                .withPlaceholderPalette(plugin.getPlaceholderPalette());

        CompletableFuture<SpectateResponse<EnderSpectatorInventory>> pwiFuture = null;

        if (args.length > 1 && api instanceof PerWorldInventorySeeApi) {
            String pwiArgument = StringHelper.joinArray(" ", 1, args);
            PerWorldInventorySeeApi pwiApi = (PerWorldInventorySeeApi) api;

            Either<String, PwiCommandArgs> either = PwiCommandArgs.parse(pwiArgument, pwiApi.getHook());
            if (either.isLeft()) {
                player.sendMessage("§c§l✖§r §9Инвентарь §8» §f" + either.getLeft());
                return true;
            }

            PwiCommandArgs pwiOptions = either.getRight();
            CompletableFuture<Optional<UUID>> uuidFuture = isUuid
                    ? CompletableFuture.completedFuture(Optional.of(uuid))
                    : pwiApi.fetchUniqueId(playerNameOrUUID);

            final boolean finalIsUuid = isUuid;
            pwiFuture = uuidFuture.thenCompose(optId -> {
                if (optId.isPresent()) {
                    UUID uniqueId = optId.get();
                    ProfileId profileId = new ProfileId(pwiApi.getHook(), pwiOptions, uniqueId);
                    CompletableFuture<String> userNameFuture = finalIsUuid
                            ? api.fetchUserName(uniqueId).thenApply(o -> o.orElse("InvSee++ Player")).exceptionally(t -> "InvSee++ Player")
                            : CompletableFuture.completedFuture(playerNameOrUUID);
                    return userNameFuture.thenCompose(playerName -> pwiApi.spectateEnderChest(uniqueId, playerName, creationOptions, profileId));
                } else {
                    return CompletableFuture.completedFuture(SpectateResponse.fail(NotCreatedReason.targetDoesNotExists(target)));
                }
            });
        }

        //TODO Multiverse-Inventories


        CompletableFuture<OpenResponse<EnderSpectatorInventoryView>> fut;

        if (pwiFuture != null) {
            fut = pwiFuture.thenApply(response -> response.isSuccess()
                    ? ((PerWorldInventorySeeApi) api).openEnderSpectatorInventory(player, response.getInventory(), creationOptions)
                    : OpenResponse.closed(NotOpenedReason.notCreated(response.getReason())));
        }

        //TODO else if (mviFuture != null) { ... }

        else {
            //No PWI argument - just continue with the regular method

            if (isUuid) {
                final UUID finalUuid = uuid;
                fut = api.fetchUserName(uuid).thenApply(o -> o.orElse("InvSee++ Player")).exceptionally(t -> "InvSee++ Player")
                        .thenCompose(userName -> api.spectateEnderChest(player, finalUuid, userName, creationOptions));
            } else {
                fut = api.spectateEnderChest(player, playerNameOrUUID, creationOptions);
            }
        }

        //Gracefully handle failure and faults
        fut.whenComplete((openResponse, throwable) -> {
            if (throwable != null) {
                player.sendMessage("§c§l✖§r §9Инвентарь §8» §fПроизошла §6ошибка§f при открытии эндер-сундука §7" + playerNameOrUUID + "§f.");
                plugin.getLogger().log(Level.SEVERE, "§c§l✖§r §9Система §8» §fОшибка при создании §6эндер-сундука§f для просмотра", throwable);
            } else {
                if (!openResponse.isOpen()) {
                    NotOpenedReason notOpenedReason = openResponse.getReason();
                    if (notOpenedReason instanceof InventoryOpenEventCancelled) {
                        player.sendMessage("§c§l✖§r §9Инвентарь §8» §fДругой плагин §6заблокировал§f просмотр эндер-сундука §7" + playerNameOrUUID + "§f.");
                    } else if (notOpenedReason instanceof InventoryNotCreated) {
                        NotCreatedReason reason = ((InventoryNotCreated) notOpenedReason).getNotCreatedReason();
                        if (reason instanceof TargetDoesNotExist) {
                            player.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрока §7" + playerNameOrUUID + " §6не существует§f.");
                        } else if (reason instanceof UnknownTarget) {
                            player.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрок §7" + playerNameOrUUID + " §fещё не §6заходил на сервер§f.");
                        } else if (reason instanceof TargetHasExemptPermission) {
                            player.sendMessage("§c§l✖§r §9Инвентарь §8» §fИгрок §7" + playerNameOrUUID + " §fимеет §6защиту§f от просмотра эндер-сундука.");
                        } else if (reason instanceof ImplementationFault) {
                            player.sendMessage("§c§l✖§r §9Инвентарь §8» §fОшибка при §6загрузке эндер-сундука§f игрока §7" + playerNameOrUUID + "§f.");
                        } else if (reason instanceof OfflineSupportDisabled) {
                            player.sendMessage("§c§l✖§r §9Инвентарь §8» §fПросмотр эндер-сундуков §6оффлайн-игроков §fотключён.");
                        } else {
                            player.sendMessage("§c§l✖§r §9Инвентарь §8» §fНе удалось §6создать эндер-сундук §7" + playerNameOrUUID + " §fпо неизвестной причине.");
                        }
                    } else {
                        player.sendMessage("§c§l✖§r §9Инвентарь §8» §fНе удалось открыть эндер-сундук §7" + playerNameOrUUID + " §fпо неизвестной причине.");
                    }
                } //else: it opened successfully: nothing to do there!
            }
        });

        return true;
    }

}
