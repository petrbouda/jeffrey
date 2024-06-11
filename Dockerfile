FROM node:22

RUN wget https://dlcdn.apache.org/maven/maven-3/3.9.7/binaries/apache-maven-3.9.7-bin.tar.gz \
    && tar -xvf apache-maven-3.9.7-bin.tar.gz \
    && mv apache-maven-3.9.7 /opt

ENV M2_HOME=/opt/apache-maven-3.9.7
ENV PATH="$M2_HOME/bin:$PATH"

RUN wget https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.1%2B8/OpenJDK22U-jdk_x64_linux_hotspot_22.0.1_8.tar.gz -P /tmp
RUN tar -xvf /tmp/OpenJDK22U-jdk_x64_linux_hotspot_22.0.1_8.tar.gz -C /tmp
RUN mv /tmp/jdk-22.0.1+8 /opt

ENV JAVA_HOME=/opt/jdk-22.0.1+8
ENV PATH="$JAVA_HOME/bin:$PATH"

CMD ["java"]
