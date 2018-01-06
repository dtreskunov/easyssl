#!/usr/bin/env ruby

require 'fileutils'
require 'erb'

Entity = Struct.new(:name, :dn, :ca_name, :key_pass, :key_pkcs8, :alt_names) do
  def self.initialize(hash)
    e = Entity.new
    hash.each do |k, v|
      e[k] = v
    end
    e
  end

  def self.cnf_content_template
    @cnf_content_template ||= ERB.new <<-EOF
[ ca ]
default_ca = CA_default
[ CA_default ]
database = <%= index_txt %>
default_md = default
default_crl_days = 30
[ req ]
x509_extensions = ext
distinguished_name = req_distinguished_name
prompt = no
[ req_distinguished_name ]
<%= dn.split('/').reject(&:empty?).join("\n") %>
[ ext ]
basicConstraints = CA:<%= ca_name ? 'FALSE' : 'TRUE' %>
#keyUsage = digitalSignature, keyEncipherment
<% unless alt_names.nil? %>
subjectAltName = @alt_names
[ alt_names ]
<% alt_names.each_with_index do |alt_name, i| %>
DNS.<%= i+1 %> = <%= alt_name %>
<% end %>
<% end %>
EOF
  end

  def key
    "#{name}/key.pem"
  end

  def csr
    "#{name}/csr.pem"
  end

  def cert
    "#{name}/cert.pem"
  end

  def cert_chain
    "#{name}/cert_chain.pem"
  end

  def cnf
    "#{name}/openssl.cnf"
  end

  def index_txt
    "#{name}/index.txt"
  end

  def cnf_content
    self.class.cnf_content_template.result(binding)
  end

  def crl
    "#{name}/crl.pem"
  end

  def gen
    puts "*** Generating certificates in #{name} ***"
    # all the files go into the directory with the corresponding name
    FileUtils.mkdir_p name

    # Create OpenSSL config file (why can't these be command-line params?)
    File.open(cnf, 'w') { |file| file.puts cnf_content }

    # Create private key
    if key_pkcs8
      if key_pass
        `openssl ecparam -genkey -name secp256r1 | openssl ec | openssl pkcs8 -out #{key} -topk8 -v1 PBE-SHA1-RC4-128 -passout pass:#{key_pass}`
      else
        `openssl ecparam -genkey -name secp256r1 | openssl ec | openssl pkcs8 -out #{key} -topk8 -nocrypt`
      end
    else
      if key_pass
        `openssl ecparam -genkey -name secp256r1 | openssl ec -out #{key} -aes128 -passout pass:#{key_pass}`
      else
        `openssl ecparam -genkey -name secp256r1 | openssl ec -out #{key}`
      end
    end

    if ca_name
      # Create CSR
      `openssl req -new -key #{key} #{key_pass ? "-passin pass:"+key_pass : ''} -out #{csr} -config #{cnf}`
      # Have the CA sign the CSR
      `openssl x509 -req -in #{csr} -CA #{ca_name}/cert.pem -CAkey #{ca_name}/key.pem -CAcreateserial -days 3650 -sha256 -out #{cert} -extensions ext -extfile #{cnf}`
      # Concatenate the CA's cert with the entity's cert
      cat(cert_chain, cert, "#{ca_name}/cert.pem")
    else
      # Create root certificate
      `openssl req -x509 -new -nodes -key #{key} -days 3650 -sha256 -out #{cert} -config #{cnf}`
      # Create empty index.txt
      FileUtils.touch(index_txt)
    end
  end

  def gen_crl
    puts "*** Generating CRL #{crl} ***"
    # This uses index.txt to create the CRL
    `openssl ca -gencrl -config #{cnf} -cert #{cert} -keyfile #{key} -out #{crl}`
  end

  def revoke(name)
    puts "*** Revoking #{name} (self: #{self.name}) ***"
    # This updates index.txt
    `openssl ca -revoke #{name}/cert.pem -config #{cnf} -cert #{cert} -keyfile #{key}`
  end

  def cat(dest, *files)
    File.open(dest, 'w') do |output|
      files.each do |file|
        File.open(file) do |input|
          output.write(input.read)
        end
      end
    end
  end
end

entities = {}

[
  Entity.initialize(name: 'ca',                 dn: '/CN=EasySSL CA'),
  Entity.initialize(name: 'another_ca',         dn: '/CN=EasySSL Another CA'),
  Entity.initialize(name: 'fake_ca',            dn: '/CN=EasySSL Fake CA'),
  Entity.initialize(name: 'localhost1',         dn: '/OU=Localhost1/CN=localhost',      ca_name: 'ca', key_pass: 'localhost1-password'),
  Entity.initialize(name: 'localhost2',         dn: '/OU=Localhost2/CN=localhost',      ca_name: 'ca', key_pass: 'localhost2-password', key_pkcs8: true),
  Entity.initialize(name: 'fake_localhost1',    dn: '/OU=Fake Localhost1/CN=localhost', ca_name: 'fake_ca'),
  Entity.initialize(name: 'ECEncryptedPKCS8',   dn: '/CN=ECEncryptedPKCS8',             ca_name: 'ca', key_pass: 'ECEncryptedPKCS8', key_pkcs8: true),
  Entity.initialize(name: 'ECEncryptedOpenSsl', dn: '/CN=ECEncryptedOpenSsl',           ca_name: 'ca', key_pass: 'ECEncryptedOpenSsl'),
  Entity.initialize(name: 'ECPlainPKCS8',       dn: '/CN=ECPlainPKCS8',                 ca_name: 'ca', key_pkcs8: true),
  Entity.initialize(name: 'ECPlainOpenSsl',     dn: '/CN=ECPlainOpenSsl',               ca_name: 'ca'),
].each do |entity|
  entity.gen
  entities[entity.name] = entity
end

entities['ca'].revoke('localhost2')
entities['ca'].gen_crl
