package pl.psobiech.opengr8on.vclu.interfaces;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.xml.interfaces.InterfaceRegistry;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipFile;

import static java.net.http.HttpRequest.newBuilder;
import static pl.psobiech.opengr8on.vclu.system.objects.BaseHttpObject.METHOD_GET;
import static pl.psobiech.opengr8on.vclu.system.objects.BaseHttpObject.USER_AGENT_HEADER;
import static pl.psobiech.opengr8on.vclu.system.objects.HttpRequest.asKeyValueArray;

public class DeviceInterfaces {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceInterfaces.class);

    private static final int TIMEOUT = 9_000;

    private static final String DEVICE_INTERFACES_URL = "http://objectmanager.grenton.com/interfaces/v4";

    private static final String DEVICE_INTERFACES_LATEST = "device-interfaces.current";

    private static final String USER_AGENT = "Java-http-client/11.0.15";

    private static final HttpClient httpClient = HttpClient.newBuilder()
                                                           .proxy(ProxySelector.getDefault())
                                                           .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_NONE))
                                                           .connectTimeout(Duration.ofMillis(TIMEOUT))
                                                           .executor(ThreadUtil.virtualExecutor(DeviceInterfaces.class.getSimpleName()))
                                                           .version(HttpClient.Version.HTTP_1_1)
                                                           .build();

    private DeviceInterfaces() {
        // NOP
    }

    public static InterfaceRegistry refresh(Path deviceInterfacesPath) {
        final Optional<Path> deviceInterfacesArchive = downloadDeviceInterfacesArchive();
        if (deviceInterfacesArchive.isPresent()) {
            final Path temporaryPath = deviceInterfacesArchive.get();
            try {
                final InterfaceRegistry interfaceRegistry;
                try (var zipFile = new ZipFile(temporaryPath.toFile())) {
                    interfaceRegistry = new InterfaceRegistry(zipFile);
                } catch (IOException | RuntimeException e) {
                    LOGGER.error("Error loading: {}", temporaryPath, e);

                    return fallbackInterfaceRegistry(deviceInterfacesPath);
                }

                FileUtil.linkOrCopy(temporaryPath, deviceInterfacesPath);

                return interfaceRegistry;
            } finally {
                FileUtil.deleteQuietly(temporaryPath);
            }
        }

        return fallbackInterfaceRegistry(deviceInterfacesPath);
    }

    private static InterfaceRegistry fallbackInterfaceRegistry(Path deviceInterfacesPath) {
        if (Files.exists(deviceInterfacesPath)) {
            try (var zipFile = new ZipFile(deviceInterfacesPath.toFile())) {
                return new InterfaceRegistry(zipFile);
            } catch (IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return InterfaceRegistry.EMPTY;
    }

    private static Optional<Path> downloadDeviceInterfacesArchive() {
        final Optional<String> deviceInterfacesLocation = downloadDeviceInterfacesLocation();
        if (deviceInterfacesLocation.isEmpty()) {
            return Optional.empty();
        }

        final Path temporaryFile = FileUtil.temporaryFile();

        final HttpResponse<Path> httpResponse;
        try {
            httpResponse = httpClient.send(createRequest(URI.create(DEVICE_INTERFACES_URL + "/" + deviceInterfacesLocation.get())), BodyHandlers.ofFile(temporaryFile));
        } catch (IOException | InterruptedException | RuntimeException e) {
            FileUtil.deleteQuietly(temporaryFile);

            LOGGER.error(e.getMessage(), e);

            return Optional.empty();
        }

        return Optional.ofNullable(
                httpResponse.body()
        );
    }

    private static Optional<String> downloadDeviceInterfacesLocation() {
        final HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(createRequest(URI.create(DEVICE_INTERFACES_URL + "/" + DEVICE_INTERFACES_LATEST)), BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            LOGGER.error(e.getMessage(), e);

            return Optional.empty();
        }

        return Optional.ofNullable(
                StringUtils.stripToNull(
                        httpResponse.body()
                )
        );
    }

    private static HttpRequest createRequest(URI uri) {
        final Duration timeout = Duration.ofMillis(TIMEOUT);

        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put(USER_AGENT_HEADER, USER_AGENT);

        return newBuilder()
                .method(
                        METHOD_GET,
                        BodyPublishers.noBody()
                )
                .uri(uri)
                .timeout(timeout)
                .headers(asKeyValueArray(headers))
                .build();
    }
}
