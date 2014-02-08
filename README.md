EditMail
========

##Setup

You need any IDE which supports the gradle build system. I personally use the [Android Studio][as].
You also need to install the [JDK (Java Development Kit)][jdk].

##Dependencies
The dependencies are solved by the gradle build.

 - [UiWorker][uiworker] ([RKSPL][rkspl]) is used for UI related updates.
 - [LazyWorker][lazyworker] ([RKSPL][rkspl]) is used for checking the email addresses delayed.
 - Minimal Android version 2.2

##Used third party libs
 - [javadns][javadns] ([BSD License][bsd3]): An implementation of the DNS protocol in Java.

##Example
Minimal example when using xml layout files:

    <eu.rekisoft.android.editmail.EditMail
        android:layout_width="fill_parent"
        android:layout_height="wrap_content" />

This will generate a normal EditText which shows errors if the email address cannot be validated.
In case of network trouble nothing no errors will be shown.

##License
This code is licensed under the [Rekisoft Public License][rkspl].
See [http://www.rekisoft.eu/licenses/rkspl.html][rkspl] for more informations.

##TODO

- Make delays customizable
- Add example how to use custom domain list
- Upload and link demo app to the play store


  [as]: http://developer.android.com/sdk/installing/studio.html
  [jdk]: http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
  [uiworker]:https://github.com/rekire/UiWorker
  [lazyworker]: https://github.com/rekire/LazyWorker
  [javadns]: http://www.dnsjava.org/
  [bsd3]: http://opensource.org/licenses/BSD-3-Clause
  [rkspl]: http://www.rekisoft.eu/licenses/rkspl.html