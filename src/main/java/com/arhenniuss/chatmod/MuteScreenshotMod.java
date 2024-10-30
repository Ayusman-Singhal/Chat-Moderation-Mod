package com.arhenniuss.chatmod;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.arhenniuss.chatmod.ServerCommunication;


@Mod(modid = MuteScreenshotMod.MODID, version = MuteScreenshotMod.VERSION)
public class MuteScreenshotMod {
    public static final String MODID = "chatmod";
    public static final String VERSION = "1.8";

    private static final List<String> bannedWords = new ArrayList<>();
    private static final List<String> worseSlangs = new ArrayList<>();
    private static final List<String> beggingPhrases = new ArrayList<>();    
    private static final Map<String, List<MessageEntry>> chatMessageMap = new HashMap<>();
    private static final List<String> EXCLUDED_RANKS = Arrays.asList("STAFF", "DEPUTY", "ADMIN", "CO-OWNER", "YT-MANAGER", "OWNER", "MOD", "GM", "MAIN ADMIN", "HELPER", "JRHELPER", "Arhenniuss", "CURATOR", "DEVELOPER");

    private static final Map<String, ChatEntry> compactChatMap = new HashMap<>();
    private static final DecimalFormat decimalFormat = new DecimalFormat("#,###");
    private static final ConcurrentHashMap<String, Long> pendingMutes = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Pattern MUTE_ANNOUNCEMENT_PATTERN = Pattern.compile("\\[CHAT\\] §b\\[STAFF\\] §f§e§9§9\\[\\w+\\] \\w+ §cmuted §e§7§7(\\w+)§c!");
    private static final Pattern CHAT_TIMESTAMP_REGEX = Pattern.compile("^(?:\\[\\d\\d:\\d\\d(:\\d\\d)?(?: AM| PM|)]|<\\d\\d:\\d\\d>) ");

    @Mod.Instance(MODID)
    public static MuteScreenshotMod instance;

    private boolean modEnabled = true;
    private boolean spamCheckEnabled = true;
    private boolean slangsCheckEnabled = true;
    private boolean beggingCheckEnabled = true;
    private static final int SPAM_THRESHOLD = 3;
    private static final long SPAM_TIME_WINDOW = 30000; // 30 seconds
    private ServerCommunication serverCommunication;
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        loadBannedWords();
        MinecraftForge.EVENT_BUS.register(new ChatEventHandler());
        registerCommands();
        System.out.println("ChatMod initialized");
        serverCommunication = new ServerCommunication();
        serverCommunication.connect(); // This now connects to your Azure Web App
    }

    private void registerCommands() {
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override
            public String getCommandName() {
                return "chat";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/chat <subcommand> [arguments]";
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) throws CommandException {
                if (args.length < 1) {
                    throw new CommandException("Usage: " + getCommandUsage(sender));
                }

                String subCommand = args[0].toLowerCase();
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

                switch (subCommand) {
                    case "on":
                        setChatModEnabled(sender, true);
                        spamCheckEnabled = true;
                        slangsCheckEnabled = true;
                        beggingCheckEnabled = true;
                        break;
                    case "off":
                        setChatModEnabled(sender, false);
                        spamCheckEnabled = false;
                        slangsCheckEnabled = false;
                        beggingCheckEnabled = false;
                        break;
                    case "status":
                        showStatus(sender);
                        break;
                    case "spam":
                        toggleSpamCheck(sender, subArgs);
                        break;
                    case "slang":
                        toggleSlangCheck(sender, subArgs);
                        break;
                    case "begging":
                        toggleBeggingCheck(sender, subArgs);
                        break;
                    case "settings":
                        openSettingsGUI(sender);
                        break;
                    default:
                        throw new CommandException("Unknown subcommand. Available subcommands: on, off, status, spam, slang, begging, add, settings");
                }
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 0;
            }

            @Override
            public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
                if (args.length == 1) {
                    return getListOfStringsMatchingLastWord(args, "on", "off", "status", "spam", "slang", "begging", "add", "settings", "timestamp");
                } else if (args.length == 2) {
                    switch (args[0].toLowerCase()) {
                        case "spam":
                        case "slang":
                        case "begging":
                        case "timestamp":
                            return getListOfStringsMatchingLastWord(args, "on", "off");
                    }
                }
                return null;
            }
        });
    }

    private void setChatModEnabled(ICommandSender sender, boolean enabled) {
        modEnabled = enabled;
        sender.addChatMessage(new ChatComponentText(enabled ? "§aChatMod enabled." : "§cChatMod disabled."));
    }
    
    private void showStatus(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText((beggingCheckEnabled ? "§a" : "§c") + ("Chat Mod: ") + (modEnabled ? "\u2714" : "\u2718")));
        sender.addChatMessage(new ChatComponentText((beggingCheckEnabled ? "§a" : "§c") + ("Spam Check: ") + (spamCheckEnabled ? "\u2714" : "\u2718")));
        sender.addChatMessage(new ChatComponentText((beggingCheckEnabled ? "§a" : "§c") + ("Slang check: ")+ (slangsCheckEnabled ? "\u2714" : "\u2718")));
        sender.addChatMessage(new ChatComponentText((beggingCheckEnabled ? "§a" : "§c") + ("Begging Check: ") + (beggingCheckEnabled ? "\u2714" : "\u2718")));
        sender.addChatMessage(new ChatComponentText("§eSpam Time: " + (SPAM_TIME_WINDOW / 1000) + " sec"));
    }

    private void toggleSpamCheck(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            throw new CommandException("Usage: /chat spam <on|off>");
        }
        spamCheckEnabled = args[0].equalsIgnoreCase("on");
        sender.addChatMessage(new ChatComponentText(spamCheckEnabled ? "§aSpam check enabled." : "§cSpam check disabled."));
    }

    private void toggleSlangCheck(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            throw new CommandException("Usage: /chat slang <on|off>");
        }
        slangsCheckEnabled = args[0].equalsIgnoreCase("on");
        sender.addChatMessage(new ChatComponentText(slangsCheckEnabled ? "§aSlangs check enabled." : "§cSlangs check disabled."));
    }

    private void toggleBeggingCheck(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            throw new CommandException("Usage: /chat begging <on|off>");
        }
        beggingCheckEnabled = args[0].equalsIgnoreCase("on");
        sender.addChatMessage(new ChatComponentText(beggingCheckEnabled ? "§aBegging check enabled." : "§cBegging check disabled."));
    }
    // Getter and setter methods for the GUI
    public boolean isModEnabled() {
        return modEnabled;
    }

    public void setModEnabled(boolean modEnabled) {
        this.modEnabled = modEnabled;
    }

    public boolean isSpamCheckEnabled() {
        return spamCheckEnabled;
    }

    public void setSpamCheckEnabled(boolean spamCheckEnabled) {
        this.spamCheckEnabled = spamCheckEnabled;
    }

    public boolean isSlangsCheckEnabled() {
        return slangsCheckEnabled;
    }

    public void setSlangsCheckEnabled(boolean slangsCheckEnabled) {
        this.slangsCheckEnabled = slangsCheckEnabled;
    }
    
    public boolean isBeggingCheckEnabled() {
        return beggingCheckEnabled;
    }

    public void setBeggingCheckEnabled(boolean beggingCheckEnabled) {
        this.beggingCheckEnabled = beggingCheckEnabled;
    }
    
    private void loadBannedWords() {
        loadWordsFromFile("banned_words.txt", bannedWords);
        loadWordsFromFile("worse_slangs.txt", worseSlangs);
        loadWordsFromFile("begging_phrases.txt", beggingPhrases);
    }

    private void openSettingsGUI(ICommandSender sender) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft.getMinecraft().displayGuiScreen(new ChatModSettingsGUI(MuteScreenshotMod.this));
        });
    }
    
    private void loadWordsFromFile(String fileName, List<String> wordList) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                wordList.add(line.trim().toLowerCase());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long currentTime = System.currentTimeMillis();
            compactChatMap.entrySet().removeIf(entry -> 
                currentTime - entry.getValue().lastSeenMessageMillis > 30000);
        }
        if (event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getMinecraft();
        }
    } 
    
    public class ChatEventHandler {
    	
    	
        @SideOnly(Side.CLIENT)
        @SubscribeEvent
        public void onClientChatReceived(ClientChatReceivedEvent event) {
            if (!modEnabled || event.type == 2) {
                return;
            }
            
            IChatComponent chat = event.message;
            String message = event.message.getUnformattedText();

            if (isSkyblockStatusMessage(message) || isStaffChatMessage(message)) {
                return;
            }

            Minecraft mc = Minecraft.getMinecraft();

            String senderName = extractSenderName(message);
            String messageContent = extractMessageContent(message);

            Matcher matcher = MUTE_ANNOUNCEMENT_PATTERN.matcher(message);
            if (matcher.find()) {
                String mutedPlayer = matcher.group(1);
                if (pendingMutes.remove(mutedPlayer) != null) {
                    System.out.println("Mute for " + mutedPlayer + " has been handled by another staff member.");
                }
                return;
            }

            if (spamCheckEnabled) {
                checkForSpam(mc, senderName, messageContent);
            }

            if (slangsCheckEnabled) {
                checkForBannedWords(mc, senderName, messageContent);
            }           
            handleCompactChat(event);
        }

    	private boolean isSkyblockStatusMessage(String message) {
    	    // This pattern matches various Skyblock status message formats
    		String pattern = "^\\u00A7[a-f0-9](\\d+/\\d+\u2764|\\d+\u2764)\\s+\\u00A7a\\u00A7[a-f0-9](\\d+\u2747 Defense|\u23F3)\\s+\\u00A7b\\d+/\\d+\u270E Mana \\u00A73\\d+\\u00A73\u02AC.*";
    	    return message.matches(pattern);
    	}

        private boolean isStaffChatMessage(String message) {
            return message.contains("[STAFF]") || message.startsWith("§b[STAFF]");
        }
        
        private void checkForSpam(Minecraft mc, String senderName, String messageContent) {
            if (senderName.equals("UnknownPlayer")) {
                System.out.println("Skipping spam check for UnknownPlayer");
                return;
            }

            if (mc.thePlayer != null && 
                    !mc.thePlayer.getName().equals(senderName) && 
                    isValidPlayerName(senderName) && 
                    !senderName.equals("Profile") &&
                    !senderName.equals("Kit") &&
                    !senderName.equals("https") &&
                    !senderName.equals("friend") &&
                    !senderName.equals("Staff") &&
                    !senderName.startsWith("EXCLUDED_")) {

                long currentTime = System.currentTimeMillis();

                if (!chatMessageMap.containsKey(senderName)) {
                    chatMessageMap.put(senderName, new ArrayList<>());
                }

                List<MessageEntry> messages = chatMessageMap.get(senderName);
                messages.add(new MessageEntry(messageContent.toLowerCase(), currentTime));

                messages.removeIf(entry -> (currentTime - entry.timestamp) > SPAM_TIME_WINDOW);

                Map<String, Integer> messageCount = new HashMap<>();
                for (MessageEntry entry : messages) {
                    messageCount.put(entry.content, messageCount.getOrDefault(entry.content, 0) + 1);
                    if (messageCount.get(entry.content) >= SPAM_THRESHOLD) {
                        System.out.println("Spam detected from " + senderName + ": " + entry.content + " (Count: " + messageCount.get(entry.content) + ")");
                        handleInappropriateMessage(mc, senderName, "/mute " + senderName + " 1H mci");
                        chatMessageMap.remove(senderName);
                        return;
                    }
                }
                
                System.out.println("Message from " + senderName + " processed. Current count: " + messages.size());
            }
        }
        
        private void checkForBannedWords(Minecraft mc, String senderName, String messageContent) {
            if (senderName.startsWith("EXCLUDED_")) {
                return;
            }

            for (String beggingPhrase : beggingPhrases) {
                if (containsWholeWord(messageContent.toLowerCase(), beggingPhrase)) {
                    handleInappropriateMessage(mc, senderName, "/mute " + senderName + " 1H mci");
                    return;
                }
            }
            
            for (String bannedWord : bannedWords) {
                if (containsWholeWord(messageContent.toLowerCase(), bannedWord)) {
                    handleInappropriateMessage(mc, senderName, "/mute " + senderName + " 4H mci");
                    return;
                }
            }

            for (String worseSlang : worseSlangs) {
                if (containsWholeWord(messageContent.toLowerCase(), worseSlang)) {
                    handleInappropriateMessage(mc, senderName, "/mute " + senderName + " 45D mji");
                    return;
                }
            }
        }

        private boolean isValidPlayerName(String name) {
            // Basic check for valid Minecraft usernames
            return name.matches("^[a-zA-Z0-9_]{3,16}$");
        }

        public void handleInappropriateMessage(Minecraft mc, String senderName, String muteCommand) {
            if (mc.thePlayer != null && 
                    !mc.thePlayer.getName().equals(senderName) && 
                    isValidPlayerName(senderName) && 
                    !senderName.equals("UnknownPlayer") &&
                    !senderName.equals("Profile") &&
                    !senderName.equals("Kit") &&
                    !senderName.equals("https") &&
                    !senderName.equals("Staff") &&
                    !senderName.startsWith("EXCLUDED_")) {

                    if (!pendingMutes.containsKey(senderName)) {
                        scheduleMute(mc, senderName, muteCommand);
                    } else {
                        System.out.println("Mute for " + senderName + " is already pending.");
                    }
                } else {
                    System.out.println("Mute not executed. Player: " + senderName + ", Command: " + muteCommand);
                }
        }

        private void scheduleMute(Minecraft mc, String senderName, String muteCommand) {
        	int randomDelay = (int) (Math.random() * 9000) + 1000; // Random delay between 1000ms (1s) and 10000ms (10s)
            pendingMutes.put(senderName, System.currentTimeMillis() + randomDelay);

            scheduler.schedule(() -> {
                if (pendingMutes.containsKey(senderName)) {
                    executeMute(mc, senderName, muteCommand);
                }
            }, randomDelay, TimeUnit.MILLISECONDS);
        }

        private void executeMute(Minecraft mc, String senderName, String muteCommand) {
            mc.thePlayer.sendChatMessage(muteCommand);
            pendingMutes.remove(senderName);
            
            // Send mute message to the dedicated server
            serverCommunication.sendMuteMessage(senderName, muteCommand);
            
            // Delay the screenshot
            scheduler.schedule(() -> {
                ScreenshotHelper.takeScreenshot(senderName, muteCommand);
            }, 750, TimeUnit.MILLISECONDS);

            System.out.println("Mute command executed: " + muteCommand);
        }
        
        private void handleCompactChat(ClientChatReceivedEvent event) {
            IChatComponent chatComponent = event.message;
            String fullMessage = chatComponent.getUnformattedText();
            
            // Skip compacting for Skyblock status messages
            if (isSkyblockStatusMessage(fullMessage)) {
                return;
            }

            String key = getMessageKey(fullMessage);
            long currentTime = System.currentTimeMillis();

            if (compactChatMap.containsKey(key)) {
                ChatEntry entry = compactChatMap.get(key);
                if (currentTime - entry.lastSeenMessageMillis <= 30000) { // 30 seconds
                    entry.messageCount++;
                    entry.lastSeenMessageMillis = currentTime;

                    // Create a new component with the counter
                    IChatComponent counterComponent = new ChatComponentText(" §7(" + entry.messageCount + ")");
                    
                    // Check if the original message already has a counter
                    String lastPart = chatComponent.getSiblings().isEmpty() ? "" : 
                        chatComponent.getSiblings().get(chatComponent.getSiblings().size() - 1).getUnformattedText();
                    if (lastPart.matches(" §7\\(\\d+\\)")) {
                        // Remove the old counter
                        chatComponent.getSiblings().remove(chatComponent.getSiblings().size() - 1);
                    }
                    
                    // Add the new counter
                    chatComponent.appendSibling(counterComponent);

                    // Update the event message
                    event.message = chatComponent;
                } else {
                    // Reset if more than 30 seconds have passed
                    compactChatMap.put(key, new ChatEntry(1, currentTime, 0));
                }
            } else {
                compactChatMap.put(key, new ChatEntry(1, currentTime, 0));
            }
        }

        @Mod.EventHandler
        public void onServerStopping(FMLServerStoppingEvent event) {
            serverCommunication.disconnect();
        }
        
        private String getMessageKey(String message) {
            // For Skyblock status messages, use the whole message as the key
            if (isSkyblockStatusMessage(message)) {
                return message;
            }

            // Extract the core message without chat flags
            String[] parts = message.split(": ", 2);
            if (parts.length > 1) {
                return parts[0] + ": " + parts[1].trim();
            }
            return message.trim();
        }

        private boolean containsWholeWord(String message, String word) {
            String[] words = message.split("\\s+");
            for (String w : words) {
                if (w.equals(word)) {
                    return true;
                }
            }
            return false;
        }

        private String extractSenderName(String message) {
            message = message.replaceAll("§.", ""); // Remove color codes

            // Pattern to match messages with ranks
            Pattern rankPattern = Pattern.compile("\\[(.*?)\\] (.*?): (.*)");
            Matcher rankMatcher = rankPattern.matcher(message);
            if (rankMatcher.find()) {
                String rank = rankMatcher.group(1).trim();
                String name = rankMatcher.group(2).trim();
                
                // Check if the rank is in the excluded list
                for (String excludedRank : EXCLUDED_RANKS) {
                    if (rank.toUpperCase().contains(excludedRank)) {
                        return "EXCLUDED_" + name;
                    }
                }
                
                return name;
            }

            // Pattern to match messages without ranks
            Pattern noRankPattern = Pattern.compile("^([^:]+): (.*)");
            Matcher noRankMatcher = noRankPattern.matcher(message);
            if (noRankMatcher.find()) {
                return noRankMatcher.group(1).trim();
            }

            // If no patterns match, return UnknownPlayer
            return "UnknownPlayer";
        }

        private String extractMessageContent(String message) {
            if (message.startsWith("<") || message.startsWith("[")) {
                int prefixEndIndex = message.indexOf("] ");
                int startIndex;

                if (prefixEndIndex != -1) {
                    startIndex = message.indexOf(": ", prefixEndIndex + 2);
                    if (startIndex != -1) {
                        return message.substring(startIndex + 2);
                    }
                } else {
                    startIndex = message.indexOf("> ");
                    if (startIndex != -1) {
                        return message.substring(startIndex + 2);
                    }
                }
            }
            if (message.contains(":")) {
                int startIndex = message.indexOf(": ");
                if (startIndex != -1) {
                    return message.substring(startIndex + 2);
                }
            }
            return message;
        }

        private String cleanColor(String in) {
            return in.replaceAll("(?i)§[0-9A-FK-OR]", "");
        }
    }

    private static class MessageEntry {
        String content;
        long timestamp;

        MessageEntry(String content, long timestamp) {
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    private static class ChatEntry {
        int messageCount;
        long lastSeenMessageMillis;
        int chatLineId;

        ChatEntry(int messageCount, long lastSeenMessageMillis, int chatLineId) {
            this.messageCount = messageCount;
            this.lastSeenMessageMillis = lastSeenMessageMillis;
            this.chatLineId = chatLineId;
        }
    }
}
