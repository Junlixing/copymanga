import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadImgTask implements Runnable {

        private Path path;

        private final List<String> imgUrlList;

        public DownloadImgTask(Path path, List<String> imgUrlList) {
            this.path = path;
            this.imgUrlList = imgUrlList;
        }

        @Override
        public void run() {
            if (!Files.exists(path)) {
                try {
                    path = Files.createDirectories(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            AtomicInteger atomic = new AtomicInteger();
            imgUrlList.forEach(imgUrl -> {
                int curInt = atomic.getAndIncrement();
                try {
                    ComicBatchDownLoad.downloadImage(imgUrl, path, String.valueOf(curInt));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }