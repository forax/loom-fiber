FROM gitpod/workspace-full

# Install custom tools, runtimes, etc.
# For example "bastet", a command-line tetris clone:
# RUN brew install bastet
#
# More information: https://www.gitpod.io/docs/config-docker/

# Install Graphviz
RUN sudo apt-get update \
    && sudo apt-get -y install graphviz

# Install Java 17
RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh && sdk install java 19.ea.1.lm-open"