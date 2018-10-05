[![Build Status](https://travis-ci.org/SolaceLabs/solace-sdkperf-amqp-plugin.svg?branch=development)](https://travis-ci.org/SolaceLabs/solace-sdkperf-amqp-plugin)

# solace-sdkperf-amqp-plugin
JMS 2.0/AMQP 1.0 plugin for solace test tool sdkperf_jms

## INTRODUCTION

The sol-sdkperf-amqp-plugin-8.2.0.1.jar implements the JMS 2.0 specification interface for messaging over the AMQP 1.0 wireline transportation. As such it can be used as a third-party plug-in within Sdkperf to enable performance testing of an AMQP 1.0 message broker.

To establish a connection to thye AMQP broker, you would run Sdkperf as follows. See below for further setup details.

    ./sdkperf_java.sh -api=thirdparty -ecc=com.solacesystems.pubsub.sdkperf.jms.amqp.AmqpJms_2_0_Client \                                -cip="amqp://<primaryBroker>,amqp://<backupBroker>"

## SDKPERF REQUIREMENTS

The  plug-in has dependencies on solace, qpid,  netty and  geronimo-jms_2.0 spec libraries, these will be extracted from mavencentral at build time. The non-solace dependencies will be included in distribution files if a distribution is built.

In order to build a new version of the JMS2.0/AMQP1.0 Sdkperf Plug-in, a full set of sdkperf libraries are required. These are included in the source, location

    libs/sol-sdkperf*

As required, these libraries can be updated to produce new versions of the plug-in should the sdkperf interface need to change.


## HOW TO BUILD THE PLUG-IN

To only build the JMS2.0/AMQP1.0 plugin build/libs/sol-sdkperf-amqp-plugin-8.2.0.1.jar: 

    ./gradlew clean build

To build a distribution for deployment into an sdkperf_java test tool build/distributions/sol-sdkperf-amqp-plugin-8.2.0.1.[tgz/zip]

    ./gradlew clean build zip tar

This will build both a zip and a tgz file.  You can exclude whichever distribution is not required.

## SOURCE AND LOADBUILD INFORMATION

The source code for this plugin is kept in the SolaceLabs github and can be obtained as follows:

    git clone git@github.com:SolaceLabs/solace-sdkperf-amqp-plugin.git

A built distribution cn be optained in the releases:

    [TODO]put something here


## CONFIGURING SDKPERF

Setting up to use this plugin is 3 steps:

1. Download the latest sdkperf_java from here:

        wget -nv -O sdkperf-java-latest.zip https://products.solace.com/download/SDKPERF_JAVA

        unzip sdkperf-java-latest.zip 

2. Either build or download plugin distribution and copy to the sdkperf sol-sdkperf-x.x.x.xx/lib/ directory

3. Extract the distribution in place. 

        tar zxf  sol-sdkperf-amqp-plugin-8.2.0.1.tgz
        or 
        unzip sol-sdkperf-amqp-plugin-8.2.0.1.zip

Sdkperf is now ready to be run.


## HOW TO RUN SDKPERF OVER KAFKA WITH THE PLUG-IN

Once the libraries are installed in the "lib/thirdparty" subdirectory of sdkperf-java then you are ready to use sdkperf. You need to specifically add two arguments to a normal sdkperf command line.

    o -api=thirdparty ==> This tells sdkperf to try and dynamically load a thirdparty implementation of it's internal client interface.

        o -ecc="ecc=com.solacesystems.pubsub.sdkperf.jms.amqp.AmqpJms_2_0_Client" ==> This is the java class to instantiate when creating clients.

Below is an example to connect and send 10 messages , mt=direct means no ack, mt=non-persistent means leader ack, mt=persistent means all-ack.

./sdkperf_java.sh  -api=thirdparty -ecc=ecc=com.solacesystems.pubsub.sdkperf.jms.amqp.AmqpJms_2_0_Client -cip=amqp://192.168.130.155:5672 -sql=TEST.AMQP.QUEUE -pql=TEST.AMQP.QUEUE -mn=10 -mr=1 -msa=100 -mt=persistent

##TROUBLESHOOTING

TBD

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Authors

See the list of [contributors](../../graphs/contributors) who participated in this project.

## License

This project is licensed under the Apache License, Version 2.0. - See the [LICENSE](LICENSE) file for details.

## Resources

For more information about Solace technology in general please visit these resources:

- The Solace Developer Portal website at: http://dev.solace.com
- Understanding [Solace technology.](http://dev.solace.com/tech/)
- Ask the [Solace community](http://dev.solace.com/community/).