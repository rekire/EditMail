EditMail
========

##Setup

You need any IDE which supports the gradle build system. I personally use the [Android Studio][as].
You also need to install the [JDK (Java Development Kit)][jdk].

This branch is work in progress, it requires still some clean up for the next release. However I use
this code productive, without any issues.

##Dependencies
The dependencies are solved by the gradle build.

 - [UiWorker][uiworker] ([RKSPL][rkspl]) is used for UI related updates.
 - [LazyWorker][lazyworker] ([RKSPL][rkspl]) is used for checking the email addresses delayed.
 - Support library for material design
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

In your gradle file you have to add this dependencies:

    compile 'eu.rekisoft.android:editmail:+@aar'
    compile 'eu.rekisoft.android:uiworker:1.0.1'
    compile 'eu.rekisoft.android:lazyworker:1.0.0'
    compile 'com.android.support:appcompat-v7:23.0.1'

That should be all. Check also the SampleApp in the repository or from the
[PlayStore](https://play.google.com/store/apps/details?id=eu.rekisoft.android.demo.editmail).

##License
This code is licensed under the [Rekisoft Public License][rkspl].  
See [http://www.rekisoft.eu/licenses/rkspl.html][rkspl] for more information.

##TODO

- Make delays customizable
- Add example how to use custom domain list


  [as]: http://developer.android.com/sdk/installing/studio.html
  [jdk]: http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
  [uiworker]:https://github.com/rekire/UiWorker
  [lazyworker]: https://github.com/rekire/LazyWorker
  [javadns]: http://www.dnsjava.org/
  [bsd3]: http://opensource.org/licenses/BSD-3-Clause
  [rkspl]: http://www.rekisoft.eu/licenses/rkspl.html