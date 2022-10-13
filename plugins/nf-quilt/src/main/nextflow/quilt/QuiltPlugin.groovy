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

package nextflow.quilt3

import groovy.transform.CompileStatic
import nextflow.plugin.BasePlugin
import nextflow.file.FileHelper
import org.pf4j.PluginWrapper
import nextflow.quilt3.nio.QuiltFileSystemProvider
/**
 * Implement the plugin entry point for Quilt support
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileStatic
class QuiltPlugin extends BasePlugin {

    QuiltPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    void start() {
        super.start()
        // register Quilt file system
        FileHelper.getOrInstallProvider(QuiltFileSystemProvider)
    }

}
