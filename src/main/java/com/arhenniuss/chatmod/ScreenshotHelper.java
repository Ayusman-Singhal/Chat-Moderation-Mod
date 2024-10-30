package com.arhenniuss.chatmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenshotHelper {
    public static void takeScreenshot(String playerName, String muteCommand) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            try {
                Thread.sleep(1000); // 1.5 seconds delay

                Minecraft mc = Minecraft.getMinecraft();
                Framebuffer framebuffer = mc.getFramebuffer();

                int width = framebuffer.framebufferWidth;
                int height = framebuffer.framebufferHeight;

                ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        int i = (x + (width * y)) * 4;
                        int r = buffer.get(i) & 0xFF;
                        int g = buffer.get(i + 1) & 0xFF;
                        int b = buffer.get(i + 2) & 0xFF;
                        image.setRGB(x, height - (y + 1), (r << 16) | (g << 8) | b);
                    }
                }

                // Parse mute command for duration and reason
                String duration = extractDuration(muteCommand);
                String reason = extractReason(muteCommand);

                // Set the screenshot file path with new naming convention
                SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyy_HHmmss");
                String timestamp = sdf.format(new Date());
                File screenshotsDir = new File(mc.mcDataDir, "mutes");
                if (!screenshotsDir.exists()) {
                    screenshotsDir.mkdirs();
                }
                String fileName = String.format("IGN-%s Mute %s %s+%s.png",
                        playerName,
                        duration,
                        reason,
                        timestamp);
                File screenshotFile = new File(screenshotsDir, fileName);

                ImageIO.write(image, "PNG", screenshotFile);
                mc.thePlayer.addChatMessage(new ChatComponentText("Screenshot taken: " + fileName));
                System.out.println("Screenshot saved: " + screenshotFile.getAbsolutePath());
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                System.out.println("Error taking screenshot: " + e.getMessage());
            }
        });
    }

    private static String extractDuration(String muteCommand) {
        if (muteCommand.contains("45D")) {
            return "45D";
        } else if (muteCommand.contains("8H")) {
            return "8H";
        } else if (muteCommand.contains("4H")) {
            return "4H";
        } else {
            return "1H"; // Default duration
        }
    }

    private static String extractReason(String muteCommand) {
        if (muteCommand.contains("mji")) {
            return "Major Chat Infraction";
        } else if (muteCommand.contains("mci")) {
            return "Minor Chat Infraction";
        } else {
            return "Chat Infraction"; // Default reason
        }
    }
}