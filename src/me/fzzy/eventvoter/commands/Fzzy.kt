package me.fzzy.eventvoter.commands

import magick.ImageInfo
import magick.MagickImage
import me.fzzy.eventvoter.Command
import me.fzzy.eventvoter.downloadTempFile
import me.fzzy.eventvoter.getFirstImage
import me.fzzy.eventvoter.seam.BufferedImagePicture
import me.fzzy.eventvoter.seam.SeamCarver
import me.fzzy.eventvoter.sendMessage
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent
import sx.blah.discord.handle.obj.IMessage
import sx.blah.discord.util.MissingPermissionsException
import sx.blah.discord.util.RequestBuffer
import java.net.URL
import javax.imageio.ImageIO

lateinit var carver: SeamCarver

class Fzzy : Command {

    override val cooldownMillis: Long = 6 * 1000
    override val attemptDelete: Boolean = false
    override val description = "Downsizes the last image sent in the channel using a seam carving algorithm"
    override val usageText: String = "-fzzy [imageUrl]"
    override val allowDM: Boolean = true

    init {
        carver = SeamCarver()
    }

    override fun runCommand(event: MessageReceivedEvent, args: List<String>) {
        val history = event.channel.getMessageHistory(10).toMutableList()
        history.add(0, event.message)
        val url: URL? = getFirstImage(history)
        if (url == null) {
            RequestBuffer.request { sendMessage(event.channel, "Couldn't find an image in the last 10 messages sent in this channel!") }
        } else {
            ProcessImageSeamCarve(url, event).start()
        }
    }
}

private class ProcessImageSeamCarve constructor(private var url: URL, private var event: MessageReceivedEvent) : Thread() {

    override fun run() {
        var processingMessage: IMessage? = null
        RequestBuffer.request { processingMessage = sendMessage(event.channel, "processing...") }

        val file = downloadTempFile(url)
        val info = ImageInfo(file.name)
        var magickImage = MagickImage(info)
        var sizeHelper = BufferedImagePicture.readFromFile(file.name)
        if (sizeHelper.width > 800 || sizeHelper.height > 800) {
            val newWidth: Int
            val newHeight: Int
            if (sizeHelper.width > sizeHelper.height) {
                newWidth = 800
                newHeight = (newWidth * sizeHelper.height) / sizeHelper.width
            } else {
                newHeight = 800
                newWidth = (newHeight * sizeHelper.width) / sizeHelper.height
            }
            magickImage = magickImage.scaleImage(newWidth, newHeight)
            magickImage.fileName = file.absolutePath
            magickImage.writeImage(info)
        }
        var image = BufferedImagePicture.readFromFile(file.name)
        val scale = carver.resize(image, image.width / 3, image.height / 3)

        ImageIO.write(scale.image, "jpg", file)

        RequestBuffer.request {
            processingMessage?.delete()
            try {
                event.channel.sendFile(file)
            } catch (e: MissingPermissionsException) {
            }
            file.delete()
        }
    }
}