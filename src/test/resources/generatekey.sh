openssl req -new -key server.key -out server.csr -subj "/C=CH/ST=Geneva/L=Geneva/O=CERN/OU=C2MON/CN=URI:URN:localhost:UA:C2MON"
openssl x509 -req -days 3650 -extfile extensions.cnf -in server.csr -signkey server.key -out server.crt
openssl pkcs12 -export -out keystore.pfx -inkey server.key -in server.crt -passin pass:password -passout pass:password
