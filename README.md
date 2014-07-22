Socket.isClosed() timing bug
============================



Introduction
------------

The documentation for `java.net.Socket.close()` states that any thread currently blocked on an I/O operation will throw a `SocketException`.  In the handling of the exception, one can call `Socket.isClosed()` and this should indicate whether the socket was closed because `close()` was called in another thread of the program (`isClosed() == true`) or because the socket was closed by some external reason, e.g. a TCP reset (`isClosed() == false`).

In the case of plain (non-SSL) sockets, this works as expected.  However, in the case of SSL sockets, it is possible that a socket closed via the `close()` method will erroneously result in `isClosed() == false`.  In this situation, it is possible to sleep the thread for a few milliseconds and then when calling `isClosed()` again, the value will have changed to `true` (the correct value).

This demonstrates some race condition in the underlying code that allows the value of `isClosed()` to change depending on how long one waits to call it after the exception is caught.



Expected vs. actual results
---------------------------

Expected: The value of `Socket.isClosed()` should always be `true` in the exception handling caught when `read()` unblocks after calling `Socket.close()` in another thread.

Actual: The value of `Socket.isClosed()`, in `SSLSocket`s after having called `Socket.close()`, is sometimes false and then, after sleeping a few milliseconds, `isClosed()` correctly returns true.



Test methodology
----------------

The demo code works by opening a socket and then starting a thread to read from it.  A brief (5 second delay) is used to let the reading thread to start and reach the call to `read()`.  Then another thread is started, which calls `Socket.close()`.  This causes the reading thread to unblock and catch an `IOException` (specifically the subclass `SocketException`).  In the handling of the exception, check `Socket.isClosed()`.  Then sleep some given number of milliseconds and check `Socket.isClosed()` again.  If the value has changed between the two calls, the bug is demonstrated.



Platforms tested
----------------

OS            | Java version | Result
------------- | ------------ | ------
Linux Mint 13 | 1.6.0_24     | could not duplicate
Linux Mint 17 | 1.7.0_51     | **occurs intermittently**
Linux Mint 16 | 1.7.0_55     | **occurs intermittently**



Execution
---------

The program requires a Java runtime version 1.4 or later.

Program arguments:

  0. Host name
  1. Port
  2. SSL mode: true=SSL, false=non SSL (the bug only seems to occur in SSL connections)
  3. Milliseconds delay between checking value of isClosed()

Example execution:

```
$ mvn clean package exec:java -Dexec.args="google.com 443 true 250"
```

Or

```
$ mvn clean package
$ java -jar target/iscloseddemo-*.jar google.com 443 true 250
```



Output
------

Execution will either demonstrate the bug, or not.  It appears to happen intermittently and so
it may require many attempts before the bug occurs.

Output when bug occurs:

```
$ java -jar target/iscloseddemo-*.jar google.com 443 true 250
Testing against host=google.com, port=443, ssl=true, sleepMs=250.
Connected socket=7c1ca8e[SSL_NULL_WITH_NULL_NULL: Socket[addr=google.com/173.194.121.9,port=443,localport=59402]]
Calling read...
SSL handshake completed: 7c1ca8e[TLS_ECDHE_ECDSA_WITH_RC4_128_SHA: Socket[addr=google.com/173.194.121.9,port=443,localport=59402]].
Calling close.

***********************
*** BUG BUG BUG BUG BUG
***
*** Value of isClosed changed from=false to=true!!!!!
***
*** BUG BUG BUG BUG BUG
***********************
```

Output when bug does not occur:

```
$ java -jar target/iscloseddemo-*.jar google.com 443 true 250
Testing against host=google.com, port=443, ssl=true, sleepMs=250.
Connected socket=7c1ca8e[SSL_NULL_WITH_NULL_NULL: Socket[addr=google.com/74.125.228.225,port=443,localport=50739]]
Calling read...
SSL handshake completed: 7c1ca8e[TLS_ECDHE_ECDSA_WITH_RC4_128_SHA: Socket[addr=google.com/74.125.228.225,port=443,localport=50739]].
Calling close.
Bug not encountered.  Value of isClosed stayed=true.
```



[Joel Geiger](mailto:joel@sibilantsolutions.com)
