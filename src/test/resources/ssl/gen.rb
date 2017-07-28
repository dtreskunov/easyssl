#!/usr/bin/env ruby

require 'fileutils'

Entity = Struct.new(:name, :dn, :ca_name, :key_pass, :key_pkcs8) do
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
    <<-EOF
[ ca ]
default_ca = CA_default
[ CA_default ]
database = #{index_txt}
default_md = default
default_crl_days = 30
EOF
  end

  def crl
    "#{name}/crl.pem"
  end

  def gen
    # all the files go into the directory with the corresponding name
    FileUtils.mkdir_p name

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
      `openssl req -new -key #{key} #{key_pass ? "-passin pass:"+key_pass : ''} -out #{csr} -subj "#{dn}"`
      # Have the CA sign the CSR
      `openssl x509 -req -in #{csr} -CA #{ca_name}/cert.pem -CAkey #{ca_name}/key.pem -CAcreateserial -days 3650 -sha256 -out #{cert}`
      # Concatenate the CA's cert with the entity's cert
      cat(cert_chain, cert, "#{ca_name}/cert.pem")
    else
      # Create root certificate
      `openssl req -x509 -new -nodes -key #{key} -days 3650 -sha256 -out #{cert} -subj "#{dn}"`
      # Create OpenSSL config file (why can't these be command-line params?)
      File.open(cnf, 'w') { |file| file.puts cnf_content }
      # Create empty index.txt
      FileUtils.touch(index_txt)
    end
  end

  def gen_crl
    # This uses index.txt to create the CRL
    `openssl ca -gencrl -config #{cnf} -cert #{cert} -keyfile #{key} -out #{crl}`
  end

  def revoke(name)
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

Dir.chdir File.dirname(__FILE__)

entities = {}

[
  Entity.new('ca',              '/CN=EasySSL CA'),
  Entity.new('fake_ca',         '/CN=EasySSL Fake CA'),
  Entity.new('localhost1',      '/OU=Localhost1/CN=localhost', 'ca', 'localhost1-password'),
  Entity.new('localhost2',      '/OU=Localhost2/CN=localhost', 'ca', 'localhost2-password', true),
  Entity.new('fake_localhost1', '/OU=Fake Localhost1/CN=localhost', 'fake_ca'),
  Entity.new('ECEncryptedPKCS8', '/CN=ECEncryptedPKCS8', 'ca', 'ECEncryptedPKCS8', true),
  Entity.new('ECEncryptedOpenSsl', '/CN=ECEncryptedOpenSsl', 'ca', 'ECEncryptedOpenSsl', false),
  Entity.new('ECPlainPKCS8', '/CN=ECPlainPKCS8', 'ca', nil, true),
  Entity.new('ECPlainOpenSsl', '/CN=ECPlainOpenSsl', 'ca', nil, false),
].each do |entity|
  entity.gen
  entities[entity.name] = entity
end

entities['ca'].revoke('localhost2')
entities['ca'].gen_crl
