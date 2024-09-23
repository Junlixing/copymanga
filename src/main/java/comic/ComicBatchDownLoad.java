package comic;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class ComicBatchDownLoad {

    private static final String targetLinkTemplate = "https://www.copymanga.tv/search?q={0}&q_type={1}";
    private static final String downloadImageDirectory = "image";
    private static final String chromeDriverPath = Objects.requireNonNull(Paths.get("")).toAbsolutePath() + "\\chromedriver\\chromedriver.exe";
    private static WebDriver driver = null;
    private static String comicWindow = null;
    private static  WebDriverWait wait = null;
    private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(4, 16, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());
    private static final List<Future<?>> downloadTaskList = new ArrayList<>();

    static {
        System.setProperty("webdriver.chrome.driver", chromeDriverPath);
        ChromeOptions options = new ChromeOptions();
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("请输入漫画名字： ");
        String comicName = scanner.nextLine();
        System.out.println("请输入漫画类型： ");
        String comicType = scanner.nextLine();
        scanner.close();

        long start = System.currentTimeMillis();
        driver.get(MessageFormat.format(targetLinkTemplate, comicName, comicType));
        //找到主漫画并切换,默认只取第一个漫画
        WebElement comicElement = waitElementCreate(By.className("exemptComicItem-txt-box")).findElement(By.tagName("a"));
        String comicUri = waitElementCreate(By.className("exemptComicItem-txt-box")).findElement(By.tagName("a")).getAttribute("href");
        System.out.println("当前漫画链接:  " + comicUri);
        String comicTitle = comicElement.findElement(By.tagName("p")).getText();
        System.out.println("当前漫画标题:  " + comicTitle);
        comicElement.click();
        changeNewestWindow(driver);
        comicWindow = driver.getWindowHandle();
        //遍历所有章节并等待下载完成
        WebElement all = waitElementCreate(By.id("default全部"));
        List<WebElement> chapterList = all.findElement(By.tagName("ul")).findElements(By.tagName("a"));
        try {
            chapterList.forEach(chapter -> {
                if (!driver.getWindowHandle().equals(comicWindow)) {
                    driver.close();
                    driver.switchTo().window(comicWindow);
                }
                String href = chapter.getAttribute("href");
                System.out.println("当前漫画章节链接: " + href);
                chapter.click();
                changeNewestWindow(driver);
                getImageListAndSaveImageList(driver);
            });

            downloadTaskList.forEach(downLoadTask -> {
                try {
                    downLoadTask.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            System.out.println("全部图片下载成功");

        } finally {
            threadPoolExecutor.shutdown();
            driver.quit();
            long end = System.currentTimeMillis();
            System.out.println("花费时间是: " + (end - start) + "毫秒");
        }
    }

    /**
     * 使用等待器等待元素定位器指定的元素出现
     * @param locator 元素定位器
     * @return 元素定位器定位到的WebElement
     */
    private static WebElement waitElementCreate(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * 切换到最新的窗口
     * @param driver 浏览器
     */
    private static void changeNewestWindow(WebDriver driver) {
        for (String windowHandle : driver.getWindowHandles()) {
            if (!windowHandle.equals(driver.getWindowHandle())) {
                if (!driver.getWindowHandle().equals(comicWindow))
                    driver.close();
                driver.switchTo().window(windowHandle);
            }
        }
    }

    /**
     * 通过滚动的方式获取所有图片并下载
     * @param driver 浏览器
     */
    private static void getImageListAndSaveImageList(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Set<String> loadedImages = new HashSet<>();
            // 初始化滚动位置
            int scrollPosition = 0;
            int scrollStep = 250;
            int maxPage = getMaxPage(driver);
            while (true) {
                //滚动页面
                js.executeScript("window.scrollBy(0, " + scrollStep + ");");
                scrollPosition += scrollStep;

                waitElementCreate(By.cssSelector("img.lazyloaded"));
                List<WebElement> lazyImages = driver.findElements(By.cssSelector("img.lazyloaded"));
                for (WebElement image : lazyImages) {
                    String imageUrl = image.getAttribute("src");
                    if (imageUrl == null || imageUrl.isEmpty()) {
                        imageUrl = image.getAttribute("data-src");
                    }
                    if (imageUrl != null && !imageUrl.isEmpty() && !loadedImages.contains(imageUrl)) {
                        loadedImages.add(imageUrl);
                        System.out.println("抓取到新图片 URL: " + imageUrl);
                    }
                }

                // 获取页面的总高度
                long newHeight = (long) js.executeScript("return document.body.scrollHeight");

                if (loadedImages.size() >= maxPage) {
                    System.out.println("已到达最大页数");
                    break;
                }

                if (scrollPosition >= newHeight) {
                    System.out.println("页面已滚动到底部，所有图片已加载。");
                    break;
                }
            }
            WebElement header = driver.findElement(By.className("header"));
            String title = header.getText();
            String[] comicTitle = title.split("/");
            String path = Paths.get("").toAbsolutePath() + "\\" + downloadImageDirectory + "\\" + comicTitle[0] + "\\" + comicTitle[1];
            List<WebElement> elements = driver.findElements(By.tagName("li"));
            List<String> imgUrlList = elements.stream()
                    .map(webElement -> webElement.findElement(By.tagName("img")).getAttribute("src"))
                    .toList();
            Future<?> downloadTask = threadPoolExecutor.submit(new DownloadImgTask(Paths.get(path), imgUrlList));
            downloadTaskList.add(downloadTask);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取当前页面漫画的总页数
     * @param driver 浏览器
     * @return 总页数
     */
    private static Integer getMaxPage(WebDriver driver) {
        return Integer.valueOf(waitElementCreate(By.className("comicCount")).getText());
    }

    private static Integer getMaxNum(WebDriver driver) {
        WebElement element = driver.findElement(By.className("comicContent-footer-txt"));
        WebElement pageList = element.findElement(By.tagName("span"));
        String pageString = pageList.getText();
        int index = pageString.length() - 1;
        for (; index >= 0; index--) {
            if (pageString.charAt(index) == ';') {
                break;
            }
        }
        return Integer.valueOf(pageString.substring(index + 1, pageString.length() - 1));
    }

    /**
     * 下载图片
     * @param imgUrl 图片地址
     * @param savePath 保存地址
     * @param fileName 文件名
     * @throws IOException
     */
    public static void downloadImage(String imgUrl, Path savePath, String fileName) throws IOException {
        try (InputStream in = new URL(imgUrl).openStream()) {
            Files.copy(in, Paths.get((savePath + "\\" + fileName + ".jpg")), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("图片已保存到: " + savePath.toString());
        } catch (IOException e) {
            System.out.println("下载失败: " + e.getMessage());
        }
    }
}
