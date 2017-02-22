/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.openecomp.policy.drools.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.FileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;


/**
 * This class audits the Maven repository
 */
public class RepositoryAudit extends DroolsPDPIntegrityMonitor.AuditBase
{
  private static final long DEFAULT_TIMEOUT = 60;	// timeout in 60 seconds

  // get an instance of logger
  private static Logger  logger = FlexLogger.getLogger(RepositoryAudit.class);		
  // single global instance of this audit object
  static private RepositoryAudit instance = new RepositoryAudit();

  /**
   * @return the single 'RepositoryAudit' instance
   */
  static DroolsPDPIntegrityMonitor.AuditBase getInstance()
  {
	return(instance);
  }

  /**
   * Constructor - set the name to 'Repository'
   */
  private RepositoryAudit()
  {
	super("Repository");
  }

  /**
   * Invoke the audit
   *
   * @param properties properties to be passed to the audit
   */
  @Override
	public void invoke(Properties properties)
	throws IOException, InterruptedException
  {
	logger.info("Running 'RepositoryAudit.invoke'");
	
	boolean isActive = true;
	String repoAuditIsActive = IntegrityMonitorProperties.getProperty("repository.audit.is.active");
	logger.debug("RepositoryAudit.invoke: repoAuditIsActive = " + repoAuditIsActive);
	
	if (repoAuditIsActive != null) {
		try {
			isActive = Boolean.parseBoolean(repoAuditIsActive.trim());
		} catch (NumberFormatException e) {
			logger.warn("RepositoryAudit.invoke: Ignoring invalid property: repository.audit.is.active = " + repoAuditIsActive);
		}
	}
	
	if(!isActive){
		logger.info("RepositoryAudit.invoke: exiting because isActive = " + isActive);
		return;
	}

	// Fetch repository information from 'IntegrityMonitorProperties'
	String repositoryId =
	  IntegrityMonitorProperties.getProperty("repository.audit.id");
	String repositoryUrl =
	  IntegrityMonitorProperties.getProperty("repository.audit.url");
	String repositoryUsername =
	  IntegrityMonitorProperties.getProperty("repository.audit.username");
	String repositoryPassword =
	  IntegrityMonitorProperties.getProperty("repository.audit.password");
	boolean upload =
	  (repositoryId != null && repositoryUrl != null
	   && repositoryUsername != null && repositoryPassword != null);

	// used to incrementally construct response as problems occur
	// (empty = no problems)
	StringBuilder response = new StringBuilder();

	long timeoutInSeconds = DEFAULT_TIMEOUT;
	String timeoutString =
	  IntegrityMonitorProperties.getProperty("repository.audit.timeout");
	if (timeoutString != null && !timeoutString.isEmpty())
	  {
		try
		  {
			timeoutInSeconds = Long.valueOf(timeoutString);
		  }
		catch (NumberFormatException e)
		  {
			logger.error
			  ("RepositoryAudit: Invalid 'repository.audit.timeout' value: '"
			   + timeoutString + "'");
			response.append("Invalid 'repository.audit.timeout' value: '")
			  .append(timeoutString).append("'\n");
			setResponse(response.toString());
		  }
	  }

	// artifacts to be downloaded
	LinkedList<Artifact> artifacts = new LinkedList<Artifact>();

	/*
	 * 1) create temporary directory
	 */
	Path dir = Files.createTempDirectory("auditRepo");
	logger.info("RepositoryAudit: temporary directory = " + dir);

	// nested 'pom.xml' file and 'repo' directory
	Path pom = dir.resolve("pom.xml");
	Path repo = dir.resolve("repo");

	/*
	 * 2) Create test file, and upload to repository
	 *    (only if repository information is specified)
	 */
	String groupId = null;
	String artifactId = null;
	String version = null;
	if (upload)
	  {
		groupId = "org.openecomp.policy.audit";
		artifactId = "repository-audit";
		version = "0." + System.currentTimeMillis();

		if (repositoryUrl.toLowerCase().contains("snapshot"))
		  {
			// use SNAPSHOT version
			version += "-SNAPSHOT";
		  }

		// create text file to write
		FileOutputStream fos =
		  new FileOutputStream(dir.resolve("repository-audit.txt").toFile());
		try
		  {
			fos.write(version.getBytes());
		  }
		finally
		  {
			fos.close();
		  }

		// try to install file in repository
		if (runProcess
			(timeoutInSeconds, dir.toFile(), null,
			 "mvn", "deploy:deploy-file",
			 "-DrepositoryId=" + repositoryId,
			 "-Durl=" + repositoryUrl,
			 "-Dfile=repository-audit.txt",
			 "-DgroupId=" + groupId,
			 "-DartifactId=" + artifactId,
			 "-Dversion=" + version,
			 "-Dpackaging=txt",
			 "-DgeneratePom=false") != 0)
		  {
			logger.error
			  ("RepositoryAudit: 'mvn deploy:deploy-file' failed");
			response.append("'mvn deploy:deploy-file' failed\n");
			setResponse(response.toString());
		  }
		else
		  {
			logger.info
			  ("RepositoryAudit: 'mvn deploy:deploy-file succeeded");

			// we also want to include this new artifact in the download
			// test (steps 3 and 4)
			artifacts.add(new Artifact(groupId, artifactId, version, "txt"));
		  }
	  }

	/*
	 * 3) create 'pom.xml' file in temporary directory
	 */
	artifacts.add(new Artifact("org.apache.maven/maven-embedder/3.2.2"));
	
	StringBuilder sb = new StringBuilder();
	sb.append
	  ("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
	   + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n"
	   + "\n"
	   + "  <modelVersion>4.0.0</modelVersion>\n"
	   + "  <groupId>empty</groupId>\n"
	   + "  <artifactId>empty</artifactId>\n"
	   + "  <version>1.0-SNAPSHOT</version>\n"
	   + "  <packaging>pom</packaging>\n"
	   + "\n"
	   + "  <build>\n"
	   + "    <plugins>\n"
	   + "      <plugin>\n"
	   + "         <groupId>org.apache.maven.plugins</groupId>\n"
	   + "         <artifactId>maven-dependency-plugin</artifactId>\n"
	   + "         <version>2.10</version>\n"
	   + "         <executions>\n"
	   + "           <execution>\n"
	   + "             <id>copy</id>\n"
	   + "             <goals>\n"
	   + "               <goal>copy</goal>\n"
	   + "             </goals>\n"
	   + "             <configuration>\n"
	   + "               <localRepositoryDirectory>")
	  .append(repo)
	  .append("</localRepositoryDirectory>\n")
	  .append("               <artifactItems>\n");
	for (Artifact artifact : artifacts)
	  {
		// each artifact results in an 'artifactItem' element
		sb.append
		  ("                 <artifactItem>\n"
		   + "                   <groupId>")
		  .append(artifact.groupId)
		  .append
		  ("</groupId>\n"
		   + "                   <artifactId>")
		  .append(artifact.artifactId)
		  .append
		  ("</artifactId>\n"
		   + "                   <version>")
		  .append(artifact.version)
		  .append
		  ("</version>\n"
		   + "                   <type>")
		  .append(artifact.type)
		  .append
		  ("</type>\n"
		   + "                 </artifactItem>\n");
	  }
	sb.append
	  ("               </artifactItems>\n"
	   + "             </configuration>\n"
	   + "           </execution>\n"
	   + "         </executions>\n"
	   + "      </plugin>\n"
	   + "    </plugins>\n"
	   + "  </build>\n"
	   + "</project>\n");
	FileOutputStream fos = new FileOutputStream(pom.toFile());
	try
	  {
		fos.write(sb.toString().getBytes());
	  }
	finally
	  {
		fos.close();
	  }

	/*
	 * 4) Invoke external 'mvn' process to do the downloads
	 */

	// output file = ${dir}/out (this supports step '4a')
	File output = dir.resolve("out").toFile();

	// invoke process, and wait for response
	int rval = runProcess
	  (timeoutInSeconds, dir.toFile(), output, "mvn", "compile");
	logger.info("RepositoryAudit: 'mvn' return value = " + rval);
	if (rval != 0)
	  {
		logger.error
		  ("RepositoryAudit: 'mvn compile' invocation failed");
		response.append("'mvn compile' invocation failed\n");
		setResponse(response.toString());
	  }

	/*
	 * 4a) Check attempted and successful downloads from output file
	 *     Note: at present, this step just generates log messages,
	 *     but doesn't do any verification.
	 */
	if (rval == 0)
	  {
		// place output in 'fileContents' (replacing the Return characters
		// with Newline)
		byte[] outputData = new byte[(int)output.length()];
		FileInputStream fis = new FileInputStream(output);
		fis.read(outputData);
		String fileContents = new String(outputData).replace('\r','\n');
		fis.close();

		// generate log messages from 'Downloading' and 'Downloaded'
		// messages within the 'mvn' output
		int index = 0;
		while ((index = fileContents.indexOf("\nDown", index)) > 0)
		  {
			index += 5;
			if (fileContents.regionMatches(index, "loading: ", 0, 9))
			  {
				index += 9;
				int endIndex = fileContents.indexOf('\n', index);
				logger.info
				  ("RepositoryAudit: Attempted download: '"
				   + fileContents.substring(index, endIndex) + "'");
				index = endIndex;
			  }
			else if (fileContents.regionMatches(index, "loaded: ", 0, 8))
			  {
				index += 8;
				int endIndex = fileContents.indexOf(' ', index);
				logger.info
				  ("RepositoryAudit: Successful download: '"
				   + fileContents.substring(index, endIndex) + "'");
				index = endIndex;
			  }
		  }
	  }

	/*
	 * 5) Check the contents of the directory to make sure the downloads
	 *    were successful
	 */
	for (Artifact artifact : artifacts)
	  {
		if (repo.resolve(artifact.groupId.replace('.','/'))
			.resolve(artifact.artifactId)
			.resolve(artifact.version)
			.resolve(artifact.artifactId + "-" + artifact.version + "."
					 + artifact.type).toFile().exists())
		  {
			// artifact exists, as expected
			logger.info("RepositoryAudit: "
							  + artifact.toString() + ": exists");
		  }
		else
		  {
			// Audit ERROR: artifact download failed for some reason
			logger.error("RepositoryAudit: "
							   + artifact.toString() + ": does not exist");
			response.append("Failed to download artifact: ")
			  .append(artifact).append('\n');
			setResponse(response.toString());
		  }
	  }

	/*
	 * 6) Use 'curl' to delete the uploaded test file
	 *    (only if repository information is specified)
	 */
	if (upload)
	  {
		if (runProcess
			(timeoutInSeconds, dir.toFile(), null,
			 "curl",
			 "--request", "DELETE",
			 "--user", repositoryUsername + ":" + repositoryPassword,
			 (repositoryUrl + "/" + groupId.replace('.', '/') + "/" +
			  artifactId + "/" + version))
			!= 0)
		  {
			logger.error
			  ("RepositoryAudit: delete of uploaded artifact failed");
			response.append("delete of uploaded artifact failed\n");
			setResponse(response.toString());
		  }
		else
		  {
			logger.info
			  ("RepositoryAudit: delete of uploaded artifact succeeded");
			artifacts.add(new Artifact(groupId, artifactId, version, "txt"));
		  }
	  }

	/*
	 * 7) Remove the temporary directory
	 */
	Files.walkFileTree
	  (dir,
	   new SimpleFileVisitor<Path>()
	   {
		 public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
		   {
			 // logger.info("RepositoryAudit: Delete " + file);
			 file.toFile().delete();
			 return(FileVisitResult.CONTINUE);
		   }

		 public FileVisitResult postVisitDirectory(Path file, IOException e)
		   throws IOException
		   {
			 if (e == null)
			   {
				 // logger.info("RepositoryAudit: Delete " + file);
				 file.toFile().delete();
				 return(FileVisitResult.CONTINUE);
			   }
			 else
			   {
				 throw(e);
			   }
		   }
	   });
  }

  /**
   * Run a process, and wait for the response
   *
   * @param timeoutInSeconds the number of seconds to wait for the
   *	process to terminate
   * @param directory the execution directory of the process
   *	(null = current directory)
   * @param stdout the file to contain the standard output
   *	(null = discard standard output)
   * @param command command and arguments
   * @return the return value of the process
   * @throws IOException, InterruptedException
   */
  static int runProcess(long timeoutInSeconds,
						File directory, File stdout, String... command)
	throws IOException, InterruptedException
  {
	ProcessBuilder pb = new ProcessBuilder(command);
	if (directory != null)
	  {
		pb.directory(directory);
	  }
	if (stdout != null)
	  {
		pb.redirectOutput(stdout);
	  }

	Process process = pb.start();
	if (process.waitFor(timeoutInSeconds, TimeUnit.SECONDS))
	  {
		// process terminated before the timeout
		return(process.exitValue());
	  }
	
	// process timed out -- kill it, and return -1
	process.destroyForcibly();
	return(-1);
  }

  /* ============================================================ */

  /**
   * An instance of this class exists for each artifact that we are trying
   * to download.
   */
  static class Artifact
  {
	String groupId, artifactId, version, type;

	/**
	 * Constructor - populate the 'Artifact' instance
	 *
	 * @param groupId groupId of artifact
	 * @param artifactId artifactId of artifact
	 * @param version version of artifact
	 * @param type type of the artifact (e.g. "jar")
	 */
	Artifact(String groupId, String artifactId, String version, String type)
	{
	  this.groupId = groupId;
	  this.artifactId = artifactId;
	  this.version = version;
	  this.type = type;
	}

	/**
	 * Constructor - populate an 'Artifact' instance
	 *
	 * @param artifact a string of the form:
	 *		"<groupId>/<artifactId>/<version>[/<type>]"
	 * @throws IllegalArgumentException if 'artifact' has the incorrect format
	 */
	Artifact(String artifact)
	{
	  String[] segments = artifact.split("/");
	  if (segments.length != 4 && segments.length != 3)
		{
		  throw(new IllegalArgumentException("groupId/artifactId/version/type"));
		}
	  groupId = segments[0];
	  artifactId = segments[1];
	  version = segments[2];
	  type = (segments.length == 4 ? segments[3] : "jar");
	}

	/**
	 * @return the artifact id in the form:
	 *		"<groupId>/<artifactId>/<version>/<type>"
	 */
	public String toString()
	{
	  return(groupId + "/" + artifactId + "/" + version + "/" + type);
	}
  }
}