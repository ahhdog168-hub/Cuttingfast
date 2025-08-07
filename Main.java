import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class VideoProcessorApplication {

    private static final String TEMP_DIR = "temp_uploads";
    private static final Random random = new Random();

    public static void main(String[] args) {
        SpringApplication.run(VideoProcessorApplication.class, args);
    }

    @PostMapping("/process")
    public void processVideo(
            @RequestParam("video") MultipartFile videoFile,
            @RequestParam("speed") float speed,
            @RequestParam("pitch") float pitch,
            @RequestParam("watermark") boolean addWatermark,
            @RequestParam("filters") boolean addFilters,
            @RequestParam("aspect") boolean changeAspect,
            HttpServletResponse response) throws IOException {

        // Create temp directory if not exists
        Path tempDir = Paths.get(TEMP_DIR);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        // Generate unique filenames
        String inputFilename = "input_" + UUID.randomUUID() + getFileExtension(videoFile.getOriginalFilename());
        String outputFilename = "output_" + UUID.randomUUID() + ".mp4";
        
        Path inputPath = tempDir.resolve(inputFilename);
        Path outputPath = tempDir.resolve(outputFilename);

        try {
            // Save uploaded file
            videoFile.transferTo(inputPath.toFile());

            // Build FFmpeg command based on parameters
            String ffmpegCommand = buildFFmpegCommand(inputPath.toString(), outputPath.toString(), 
                    speed, pitch, addWatermark, addFilters, changeAspect);

            // Execute FFmpeg
            executeFFmpegCommand(ffmpegCommand);

            // Set response headers
            response.setContentType("video/mp4");
            response.setHeader("Content-Disposition", "attachment; filename=processed_video.mp4");

            // Stream the processed file back
            Files.copy(outputPath, response.getOutputStream());
            response.flushBuffer();

        } finally {
            // Clean up temp files
            Files.deleteIfExists(inputPath);
            Files.deleteIfExists(outputPath);
        }
    }

    private String buildFFmpegCommand(String inputPath, String outputPath, 
            float speed, float pitch, boolean watermark, boolean filters, boolean aspect) {
        
        StringBuilder command = new StringBuilder("ffmpeg -i ")
            .append(escapePath(inputPath));

        // Speed adjustment
        command.append(" -filter_complex \"[0:v]setpts=").append(1/speed).append("*PTS");
        
        // Audio pitch adjustment
        command.append(";[0:a]atempo=").append(speed).append(",asetrate=44100*").append(pitch).append("[a]\"");
        
        // Visual filters
        if (filters) {
            command.append(" -vf \"colorchannelmixer=rr=0.9:gg=0.9:bb=0.9,")
                  .append("hue=h=5:s=1.1,")
                  .append("eq=contrast=1.1:brightness=0.02\"");
        }
        
        // Aspect ratio changes
        if (aspect) {
            command.append(" -aspect 16:9");
        }
        
        // Watermark
        if (watermark) {
            // This would require an actual watermark image file
            // Simplified version for demo
            command.append(" -vf \"drawtext=text='UserContent':x=10:y=H-th-10:fontsize=24:fontcolor=white@0.5\"");
        }
        
        // Output settings
        command.append(" -map 0:v -map \"[a]\" -c:v libx264 -preset fast -crf 23 -c:a aac -strict experimental ")
               .append(escapePath(outputPath));
        
        return command.toString();
    }

    private void executeFFmpegCommand(String command) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
        
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                System.out.println("[FFmpeg] " + line);
            }
        }
        
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("FFmpeg processing failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Processing interrupted", e);
        }
    }

    private String escapePath(String path) {
        return "\"" + path + "\"";
    }

    private String getFileExtension(String filename) {
        return filename != null ? filename.substring(filename.lastIndexOf(".")) : ".mp4";
    }
}
