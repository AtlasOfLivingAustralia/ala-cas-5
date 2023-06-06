#./mvnw package -DskipTests=true
java -Xmx1g -Xms1g -Dcas.standalone.configurationDirectory=/data/cas/config -Dala.password.properties=/data/cas/config/pwe.properties -jar /home/bea18c/dev/github/Atlas/ala-cas-5/target/cas-exec.war
