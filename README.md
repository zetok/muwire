# MuWire - Easy Anonymous File-Sharing

MuWire is an easy to use file-sharing program which offers anonymity using [I2P technology](http://geti2p.net).  It works on any platform Java works on, including Windows,MacOS,Linux.

It is inspired by the LimeWire Gnutella client and developped by a former LimeWire developer.

The current stable release - 0.4.6 is avaiable for download at https://muwire.com.  You can find technical documentation in the "doc" folder.

### Building

You need JRE 8 or newer.  After installing that and setting up the appropriate paths, just type

```
./gradlew clean assemble 
```

If you want to run the unit tests, type
```
./gradlew clean build
```

Some of the UI tests will fail because they haven't been written yet :-/

### Running

After you build the application, look inside `gui/build/distributions`.  Untar/unzip one of the `shadow` files and then run the jar contained inside by typing `java -jar MuWire-x.y.z.jar` in a terminal or command prompt.  

If you have an I2P router running on the same machine that is all you need to do.  If you use a custom I2CP host and port, create a file `i2p.properties` and put `i2cp.tcp.host=<host>` and `i2cp.tcp.port=<port>` in there.  On Windows that file should go into `%HOME%\AppData\Roaming\MuWire`, on Mac into `$HOME/Library/Application Support/MuWire` and on Linux `$HOME/.MuWire`

If you do not have an I2P router, pass the following switch to the Java process: `-DembeddedRouter=true`.  This will launch MuWire's embedded router.  Be aware that this causes startup to take a lot longer.

### GPG Fingerprint
471B 9FD4 5517 A5ED 101F  C57D A728 3207 2D52 5E41

You can find the full key at https://keybase.io/zlatinb
