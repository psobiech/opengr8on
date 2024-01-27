# Performance

As a simple benchmark sending checkAlive() command was chosen (which is actually executing a LUA function and then returns its result). 
In the examples, the test was run using 64 separate threads were executed for a total of 60 seconds and the test was repeated 10 times.

## Raspberry PI4 8GB

Model: Raspberry Pi 4 Model B Rev 1.4
Linux: 6.1.0-rpi7-rpi-v8 #1 SMP PREEMPT Debian 1:6.1.63-1+rpt1 (2023-11-24) aarch64 GNU/Linux
CPU: (4 x 1.8 GHz)
CLU: OpenGr8ton VCLU (Version: bcf741d) using Java 21.0.1+12-LTS

### Results

```
Requests: 86693 in 60000ms
Requests: 106785 in 60000ms
Requests: 103460 in 60000ms
Requests: 103448 in 60000ms
Requests: 96727 in 60000ms
Requests: 94481 in 60000ms
Requests: 96372 in 60000ms
Requests: 105038 in 60000ms
Requests: 105867 in 60000ms
Requests: 97741 in 60000ms
```

```
Mean    99661.2
Median  100594.5
Minimum	86693
Maximum	106785
```

Messages per second: 1676

## Odroid H3+ (Intel N6005)

Linux: 5.10.0-27-amd64 #1 SMP Debian 5.10.205-2 (2023-12-31) x86_64 GNU/Linux
CPU: Intel(R) Pentium(R) Silver N6005 @ 2.00GHz (4 x 2.3 GHz)
CLU: OpenGr8ton VCLU (Version: bcf741d) using Java 21.0.1+12-LTS

### Results

```
Requests: 280565 in 60000ms
Requests: 291381 in 60000ms
Requests: 291836 in 60000ms
Requests: 293255 in 60000ms
Requests: 297159 in 60000ms
Requests: 295443 in 60000ms
Requests: 295622 in 60000ms
Requests: 294553 in 60000ms
Requests: 245670 in 60000ms
Requests: 287704 in 60000ms
```

```
Mean:    287318.8
Median:  292545.5
Minimum: 245670
Maximum: 297159
```

Messages per second: 4875

# Benchmark Code

```java
final ExecutorService executor = ThreadUtil.daemonExecutor("Benchmark");

final LongAdder requests = new LongAdder();
final List<Future<?>> futures = new LinkedList<>();
for (int i = 0; i < 64; i++) {
    futures.add(
        executor.submit(() -> {
            try (CLUClient client = new CLUClient(networkInterface.getAddress(), device, projectCipherKey)) {
                synchronized (Main.class) {
                    Main.class.wait();
                }

                do {
                    client.checkAlive()
                          .ifPresent(aBoolean -> {
                              if (aBoolean) {
                                  requests.increment();
                              }
                          });
                } while (!(Thread.interrupted()));
            }

            return null;
        })
    );
}

Thread.sleep(100);

final long startTime = System.nanoTime();
synchronized (Main.class) {
    Main.class.notifyAll();
}

Thread.sleep(60000);
System.out.printf("Requests: %d in %dms%n", requests.sum(), TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - startTime)));
System.out.flush();
System.exit(0);
```
