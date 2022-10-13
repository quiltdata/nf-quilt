/*
 * Copyright 2022, Quilt Data Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.quilt3.nio

import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.FileSystem
import java.nio.file.FileStore
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.regex.Matcher

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.Session
import nextflow.quilt3.QuiltOpts
import nextflow.quilt3.jep.QuiltParser
/**
 * Implements FileSystem interface for Quilt registries
 * Each bucket/package pair (QuiltID) is a FileSystem
 * Every logical key is a Path
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */


// cf. https://cloud.google.com/java/docs/reference/google-cloud-nio/latest/com.google.cloud.storage.contrib.nio.CloudStorageFileSystem
// https://github.com/nextflow-io/nextflow-s3fs/tree/master/src/main/java/com/upplication/s3fs
@Slf4j
@CompileStatic
public final class QuiltFileSystem extends FileSystem {

    protected final String quiltIDS;
    protected final QuiltFileSystemProvider provider

    public QuiltFileSystem(String quiltIDS, QuiltFileSystemProvider provider) {
      this.quiltIDS = quiltIDS;
      this.provider = provider;
    }

    @Override
    String toString() {
        "QuiltFileSystem:${quiltIDS}"
    }

    void copy(QuiltPath source, QuiltPath target) {
      throw new UnsupportedOperationException("NOT Implemented 'QuiltFileSystem.copy' `$source` -> `$target`")
    }

    void delete(QuiltPath path) {
        log.debug "QuiltFileSystem.delete: $path"
        path.deinstall()
      //throw new UnsupportedOperationException("Operation 'delete' is not supported by QuiltFileSystem")
    }

    @Override
    FileSystemProvider provider() {
        return provider
    }

    @Override
    void close() throws IOException {
        // nothing to do
    }

    @Override
    boolean isOpen() {
        return true
    }

    @Override
    boolean isReadOnly() {
        return false
    }

    @Override
    String getSeparator() {
        return QuiltParser.SEP
    }

    QuiltFileAttributesView getFileAttributeView(QuiltPath path) {
        log.debug "QuiltFileAttributesView QuiltFileSystem.getFileAttributeView($path)"
        def pathString = path.toUriString()
        try {
            QuiltFileAttributes attrs = readAttributes(path)
            return new QuiltFileAttributesView(attrs)
        }
        catch (Exception e) {
            throw new IOException("Unable to get attributes for file: $pathString", e)
        }
    }

    QuiltFileAttributes readAttributes(QuiltPath path)  {
        log.debug "QuiltFileAttributes QuiltFileSystem.readAttributes($path)"
        Path installedPath = path.localPath()
        try {
            BasicFileAttributes attrs = Files.readAttributes(installedPath, BasicFileAttributes)
            return new QuiltFileAttributes(path,path.toString(),attrs)
        }
        catch (java.nio.file.NoSuchFileException e) {
            log.debug "No attributes yet for: ${installedPath}"
        }
        return null
    }

    boolean exists(QuiltPath path) {
        return path.pkg().isInstalled()
    }

    Iterable<? extends Path> getRootDirectories() {
        throw new UnsupportedOperationException("Operation 'getRootDirectories' is not supported by QuiltFileSystem")
    }

    @Override
    Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException("Operation 'getFileStores' is not supported by QuiltFileSystem")
    }

    @Override
    Set<String> supportedFileAttributeViews() {
        log.debug "Calling `supportedFileAttributeViews`: ${this}"
        return Collections.unmodifiableSet( ['basic'] as Set )
    }

    @Override
    QuiltPath getPath(String root, String... more) {
        log.debug "QuiltFileSystem.getPath`[${root}]: $more"

        QuiltParser p = QuiltParser.ForBarePath(root)
        new QuiltPath(this, p)
    }

    protected String toUriString(Path path) {
        return path instanceof QuiltPath ? ((QuiltPath)path).toUriString() : null
    }

    protected String getBashLib(Path path) {
        throw new UnsupportedOperationException("Operation 'getBashLib' is not supported by QuiltFileSystem")
        return path instanceof QuiltPath ? QuiltBashLib.script() : null
    }

    protected String getUploadCmd(String source, Path target) {
        throw new UnsupportedOperationException("Operation 'getUploadCmd' is not supported by QuiltFileSystem")
        return target instanceof QuiltPath ?  QuiltFileCopyStrategy.uploadCmd(source, target) : null
    }

    @Override
    PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException("Operation 'getPathMatcher' is not supported by QuiltFileSystem")
    }

    @Override
    UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("Operation 'getUserPrincipalLookupService' is not supported by QuiltFileSystem")
    }

    @Override
    WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Operation 'newWatchService' is not supported by QuiltFileSystem")
    }

}
