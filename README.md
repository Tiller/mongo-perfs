| Test                                                                               | Mean Time (s) | Diff Base Driver | Diff Base Reactive Driver |
|------------------------------------------------------------------------------------|---------------|------------------|---------------------------|
| Java Driver (testRaw)                                                              | 3.320         | 🟢 +0%            | 🟢 -28%                    |
| Java Reactive Driver (testReactiveRaw)                                             | 4.627         | 🔴 +39%           | 🟢 +0%                     |
| Reactor Single Block (testBlockExecutor)                                           | 4.793         | 🔴 +44%           | 🔴 +4%                     |
| Reactor Flat Map w/ Concurrency (testFlatMapWithConcurrency)                       | 8.858         | 🔴 +167%          | 🔴 +91%                    |
| Reactor Flat Map w/ Big Concurrency (testFlatMapWithBigConcurrency)                | 8.882         | 🔴 +168%          | 🔴 +92%                    |
| Reactor Flat Map w/ Concurrency & Prefetch (testFlatMapWithConcurrencyAndPrefetch) | 9.024         | 🔴 +172%          | 🔴 +95%                    |
| Reactor Parallel (testParallel)                                                    | 8.970         | 🔴 +170%          | 🔴 +94%                    |