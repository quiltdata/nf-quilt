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
package nextflow.quilt.nio

import nextflow.quilt.jep.QuiltPackage
import nextflow.quilt.jep.QuiltParser
import java.nio.file.Files
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.ProviderMismatchException
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Implements Path interface for Quilt storage
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@Slf4j
@CompileStatic
public final class QuiltPath implements Path {

    private final QuiltFileSystem filesystem
    private final QuiltParser parsed
    private final String[] paths

    public QuiltPath(QuiltFileSystem filesystem, QuiltParser parsed) {
        this.filesystem = filesystem
        this.parsed = parsed
        this.paths = parsed.paths()
        log.debug "Creating QuiltPath[$parsed]@$filesystem"
    }

    public String bucket() {
        parsed.bucket()
    }

    public String packageName() {
        parsed.packageName()
    }

    public String sub_paths() {
        parsed.path()
    }

    public QuiltPackage pkg() {
        isAbsolute() ? QuiltPackage.ForParsed(parsed) : null
    }

    public String file_key() {
        sub_paths()
    }

    Path localPath() {
        Path pkgPath = pkg().packageDest()
        assert pkgPath
        Paths.get(pkgPath.toUriString(), sub_paths())
    }

    public boolean deinstall() {
        Path path = localPath()
        log.debug "QuiltPath.deinstall: $path"
        return Files.delete(path)
    }

    @Override
    FileSystem getFileSystem() {
        return filesystem
    }

    @Override
    boolean isAbsolute() {
        log.debug "isAbsolute[${packageName()}] : ${parsed}"
        filesystem && packageName()
    }

    boolean isJustPackage() {
        !parsed.hasPath()
    }

    QuiltPath getJustPackage() {
        if (isJustPackage()) return this
        QuiltParser packageParsed = QuiltParser.ForBarePath(parsed.toPackageString())
        new QuiltPath(filesystem, packageParsed)
    }

    @Override
    Path getRoot() {
        isAbsolute() ? getJustPackage() : null
    }

    @Override
    Path getFileName() {
        log.debug "getFileName`[${this}]: paths=$paths"
        isJustPackage() ? this : new QuiltPath(filesystem, parsed.lastPath()) // IF DIRECTORY
    }

    @Override
    Path getParent() {
        log.debug "${this}.getParent: ${paths}`"
        new QuiltPath(filesystem, parsed.dropPath())
    }

    @Override
    int getNameCount() {
        paths.size()
    }

    @Override
    Path getName(int index) {
        throw new UnsupportedOperationException("Operation 'getName' is not supported by QuiltPath")
    //new QuiltPath(filesystem, packageName, sub_paths[0,index], options)
    }

    @Override
    Path subpath(int beginIndex, int endIndex) {
        QuiltParser p2 = parsed.subPath(beginIndex, endIndex)
        new QuiltPath(filesystem, p2)
    }

    @Override
    boolean startsWith(Path other) {
        startsWith(other.toString())
    }

    @Override
    boolean startsWith(String other) {
        toString().startsWith(other)
    }

    @Override
    boolean endsWith(Path other) {
        endsWith(other.toString())
    }

    @Override
    boolean endsWith(String other) {
        toString().endsWith(other)
    }

    @Override
    int compareTo(Path other) {
        return toString() <=> other.toString()
    }

    @Override
    Path normalize() {
        new QuiltPath(filesystem, parsed.normalized())
    }

    @Override
    QuiltPath resolve(Path other) {
        if (other.class != QuiltPath) {
            throw new ProviderMismatchException()
        }

        final that = (QuiltPath)other
        if (other.isAbsolute()) {
            return that
        }
        throw new UnsupportedOperationException("Operation 'resolve'[$that] is not supported by QuiltPath[$this]")
    //new QuiltPath(filesystem, packageName, other.toString(), options)
    }

    @Override
    QuiltPath resolve(String other) {
        new QuiltPath(filesystem, parsed.appendPath(other))
    }

    @Override
    Path resolveSibling(Path other) {
        throw new UnsupportedOperationException("Operation 'resolveSibling'[$other] is not supported by QuiltPath")
    }

    @Override
    Path resolveSibling(String other) {
        new QuiltPath(filesystem, parsed.dropPath().appendPath(other))
    }

    @Override
    Path relativize(Path other) {
        if (this == other) return null
        String file = (other instanceof QuiltPath) ? ((QuiltPath)other).localPath() : other.toString()
        String base = [pkg().toString(), parsed.path()].join(QuiltParser.SEP)
        log.debug "relativize[$base] in [$file]"
        int i = file.indexOf(base)
        if (i < 1) {
            throw new UnsupportedOperationException("other[$file] does not contain package[$base]")
        }

        String tail = file.substring(i + base.size())
        if (tail.size() > 0 && tail[0] == '/') tail = tail.substring(1) // drop leading "/"
        log.debug "tail[$i] -> $tail"
        Paths.get(tail)
    }

    @Override
    String toString() {
        parsed.toString()
    }

    String toUriString() {
        parsed.toUriString()
    }

    @Override
    URI toUri() {
        new URI(toUriString())
    }

    @Override
    int hashCode() {
        toString().hashCode()
    }

    @Override
    Path toAbsolutePath() {
        if (isAbsolute()) return this
        throw new UnsupportedOperationException("Operation 'toAbsolutePath' is not supported by QuiltPath")
    }

    @Override
    Path toRealPath(LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Operation 'toRealPath' is not supported by QuiltPath")
    }

    @Override
    File toFile() {
        throw new UnsupportedOperationException("Operation 'toFile' is not supported by QuiltPath")
    }

    @Override
    WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("Operation 'register' is not supported by QuiltPath")
    }

    @Override
    WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException("Operation 'register' is not supported by QuiltPath")
    }

    @Override
    Iterator<Path> iterator() {
        throw new UnsupportedOperationException("Operation 'iterator' is not supported by QuiltPath")
    }

}
