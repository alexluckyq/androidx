/*
 * Copyright 2022 The Android Open Source Project
 *
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
 */

package androidx.build

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MavenUploadHelperTest {

    @Test
    fun testSortPomDependencies() {
        /* ktlint-disable max-line-length */
        val pom = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <!-- This module was also published with a richer model, Gradle metadata,  -->
  <!-- which should be used instead. Do not delete the following line which  -->
  <!-- is to indicate to Gradle or any Gradle module metadata file consumer  -->
  <!-- that they should prefer consuming it instead. -->
  <!-- do_not_remove: published-with-gradle-metadata -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>androidx.collection</groupId>
  <artifactId>collection-jvm</artifactId>
  <version>1.3.0-alpha01</version>
  <name>Android Support Library collections</name>
  <description>Standalone efficient collections.</description>
  <url>https://developer.android.com/jetpack/androidx/releases/collection#1.3.0-alpha01</url>
  <inceptionYear>2018</inceptionYear>
  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <name>The Android Open Source Project</name>
    </developer>
  </developers>
  <scm>
    <connection>scm:git:https://android.googlesource.com/platform/frameworks/support</connection>
    <url>https://cs.android.com/androidx/platform/frameworks/support</url>
  </scm>
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-stdlib-common</artifactId>
      <version>1.6.10</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-stdlib</artifactId>
      <version>1.6.10</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>androidx.annotation</groupId>
      <artifactId>annotation</artifactId>
      <version>1.3.0</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>"""

        /*
  It should have the following comment, but is broken b/230396269

  <!-- This module was also published with a richer model, Gradle metadata,  -->
  <!-- which should be used instead. Do not delete the following line which  -->
  <!-- is to indicate to Gradle or any Gradle module metadata file consumer  -->
  <!-- that they should prefer consuming it instead. -->
  <!-- do_not_remove: published-with-gradle-metadata -->
         */
        // Expect that elements in <dependencies> are sorted alphabetically.
        val expected = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  
  
  
  
  
  <modelVersion>4.0.0</modelVersion>
  <groupId>androidx.collection</groupId>
  <artifactId>collection-jvm</artifactId>
  <version>1.3.0-alpha01</version>
  <name>Android Support Library collections</name>
  <description>Standalone efficient collections.</description>
  <url>https://developer.android.com/jetpack/androidx/releases/collection#1.3.0-alpha01</url>
  <inceptionYear>2018</inceptionYear>
  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <name>The Android Open Source Project</name>
    </developer>
  </developers>
  <scm>
    <connection>scm:git:https://android.googlesource.com/platform/frameworks/support</connection>
    <url>https://cs.android.com/androidx/platform/frameworks/support</url>
  </scm>
  <dependencies>
    <dependency>
      <groupId>androidx.annotation</groupId>
      <artifactId>annotation</artifactId>
      <version>1.3.0</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-stdlib</artifactId>
      <version>1.6.10</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-stdlib-common</artifactId>
      <version>1.6.10</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>"""
        /* ktlint-enable max-line-length */

        val actual = sortPomDependencies(pom)
        assertEquals(expected, actual)
    }

    @Test
    fun testSortGradleMetadataDependencies() {
        /* ktlint-disable max-line-length */
        val metadata = """
{
  "formatVersion": "1.1",
  "component": {
    "url": "../../collection/1.3.0-alpha01/collection-1.3.0-alpha01.module",
    "group": "androidx.collection",
    "module": "collection",
    "version": "1.3.0-alpha01",
    "attributes": {
      "org.gradle.status": "release"
    }
  },
  "createdBy": {
    "gradle": {
      "version": "7.4"
    }
  },
  "variants": [
    {
      "name": "jvmApiElements-published",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.libraryelements": "jar",
        "org.gradle.usage": "java-api",
        "org.jetbrains.kotlin.platform.type": "jvm"
      },
      "dependencies": [
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib",
          "version": {
            "requires": "1.6.10"
          }
        },
        {
          "group": "androidx.annotation",
          "module": "annotation",
          "version": {
            "requires": "1.3.0"
          }
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib-common",
          "version": {
            "requires": "1.6.10"
          }
        }
      ],
      "files": [
        {
          "name": "collection-jvm-1.3.0-alpha01.jar",
          "url": "collection-jvm-1.3.0-alpha01.jar",
          "size": 42271,
          "sha512": "b01746682f5499426492ed56cfa10e863b181f0a94e1c97de935a1d68bc1a8da9b60bbc670a71642e4c4ebde0cedbed42f08f6b305bbfa7270b3b1fa76059fa6",
          "sha256": "647d39d1ef35b45ff9b4c4b2afd7b0280431223142ededb4ee2d3ff73eb2657a",
          "sha1": "11cbbdeaa0540d0cef16567781a99cdf7b34b242",
          "md5": "309042f77be5772d725180056e5e97e9"
        }
      ]
    },
    {
      "name": "jvmRuntimeElements-published",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.libraryelements": "jar",
        "org.gradle.usage": "java-runtime",
        "org.jetbrains.kotlin.platform.type": "jvm"
      },
      "dependencies": [
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib",
          "version": {
            "requires": "1.6.10"
          }
        },
        {
          "group": "androidx.annotation",
          "module": "annotation",
          "version": {
            "requires": "1.3.0"
          }
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib-common",
          "version": {
            "requires": "1.6.10"
          }
        }
      ],
      "files": [
        {
          "name": "collection-jvm-1.3.0-alpha01.jar",
          "url": "collection-jvm-1.3.0-alpha01.jar",
          "size": 42271,
          "sha512": "b01746682f5499426492ed56cfa10e863b181f0a94e1c97de935a1d68bc1a8da9b60bbc670a71642e4c4ebde0cedbed42f08f6b305bbfa7270b3b1fa76059fa6",
          "sha256": "647d39d1ef35b45ff9b4c4b2afd7b0280431223142ededb4ee2d3ff73eb2657a",
          "sha1": "11cbbdeaa0540d0cef16567781a99cdf7b34b242",
          "md5": "309042f77be5772d725180056e5e97e9"
        }
      ]
    }
  ]
}
        """.trimIndent()

        // Expect that elements in "dependencies" are sorted alphabetically.
        val expected = """
{
  "formatVersion": "1.1",
  "component": {
    "url": "../../collection/1.3.0-alpha01/collection-1.3.0-alpha01.module",
    "group": "androidx.collection",
    "module": "collection",
    "version": "1.3.0-alpha01",
    "attributes": {
      "org.gradle.status": "release"
    }
  },
  "createdBy": {
    "gradle": {
      "version": "7.4"
    }
  },
  "variants": [
    {
      "name": "jvmApiElements-published",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.libraryelements": "jar",
        "org.gradle.usage": "java-api",
        "org.jetbrains.kotlin.platform.type": "jvm"
      },
      "dependencies": [
        {
          "group": "androidx.annotation",
          "module": "annotation",
          "version": {
            "requires": "1.3.0"
          }
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib",
          "version": {
            "requires": "1.6.10"
          }
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib-common",
          "version": {
            "requires": "1.6.10"
          }
        }
      ],
      "files": [
        {
          "name": "collection-jvm-1.3.0-alpha01.jar",
          "url": "collection-jvm-1.3.0-alpha01.jar",
          "size": 42271,
          "sha512": "b01746682f5499426492ed56cfa10e863b181f0a94e1c97de935a1d68bc1a8da9b60bbc670a71642e4c4ebde0cedbed42f08f6b305bbfa7270b3b1fa76059fa6",
          "sha256": "647d39d1ef35b45ff9b4c4b2afd7b0280431223142ededb4ee2d3ff73eb2657a",
          "sha1": "11cbbdeaa0540d0cef16567781a99cdf7b34b242",
          "md5": "309042f77be5772d725180056e5e97e9"
        }
      ]
    },
    {
      "name": "jvmRuntimeElements-published",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.libraryelements": "jar",
        "org.gradle.usage": "java-runtime",
        "org.jetbrains.kotlin.platform.type": "jvm"
      },
      "dependencies": [
        {
          "group": "androidx.annotation",
          "module": "annotation",
          "version": {
            "requires": "1.3.0"
          }
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib",
          "version": {
            "requires": "1.6.10"
          }
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib-common",
          "version": {
            "requires": "1.6.10"
          }
        }
      ],
      "files": [
        {
          "name": "collection-jvm-1.3.0-alpha01.jar",
          "url": "collection-jvm-1.3.0-alpha01.jar",
          "size": 42271,
          "sha512": "b01746682f5499426492ed56cfa10e863b181f0a94e1c97de935a1d68bc1a8da9b60bbc670a71642e4c4ebde0cedbed42f08f6b305bbfa7270b3b1fa76059fa6",
          "sha256": "647d39d1ef35b45ff9b4c4b2afd7b0280431223142ededb4ee2d3ff73eb2657a",
          "sha1": "11cbbdeaa0540d0cef16567781a99cdf7b34b242",
          "md5": "309042f77be5772d725180056e5e97e9"
        }
      ]
    }
  ]
}
        """.trimIndent()
        /* ktlint-enable max-line-length */

        val actual = sortGradleMetadataDependencies(metadata)
        assertEquals(expected, actual)
    }

    @Test
    fun testSortGradleMetadataDependenciesWithConstraints() {
        /* ktlint-disable max-line-length */
        val metadata = """
{
  "formatVersion": "1.1",
  "component": {
    "group": "androidx.activity",
    "module": "activity-ktx",
    "version": "1.5.0-alpha03",
    "attributes": {
      "org.gradle.status": "release"
    }
  },
  "createdBy": {
    "gradle": {
      "version": "7.4"
    }
  },
  "variants": [
    {
      "name": "releaseVariantReleaseApiPublication",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.libraryelements": "aar",
        "org.gradle.usage": "java-api"
      },
      "dependencies": [
        {
          "group": "androidx.activity",
          "module": "activity",
          "version": {
            "requires": "1.5.0-alpha03"
          }
        },
        {
          "group": "androidx.core",
          "module": "core-ktx",
          "version": {
            "requires": "1.1.0"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "androidx.lifecycle",
          "module": "lifecycle-runtime-ktx",
          "version": {
            "requires": "2.5.0-alpha03"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "androidx.lifecycle",
          "module": "lifecycle-viewmodel-ktx",
          "version": {
            "requires": "2.5.0-alpha03"
          }
        },
        {
          "group": "androidx.savedstate",
          "module": "savedstate-ktx",
          "version": {
            "requires": "1.2.0-alpha01"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib",
          "version": {
            "requires": "1.6.10"
          }
        }
      ],
      "files": [
        {
          "name": "activity-ktx-1.5.0-alpha03.aar",
          "url": "activity-ktx-1.5.0-alpha03.aar",
          "size": 31645,
          "sha512": "d4b175f956cd329698705ab7ecdb080c6668d689bf9ae99e8d7c53baa4383848af73c65e280baabb4938121d5d06367a900b5fc9c072eb29aa86e89b6f0c4595",
          "sha256": "e30b007d69f63a2a0c56b5275faea7badf0f80a06caa1c50b2eba7129581793e",
          "sha1": "9818a50c9ed22d6c089026f4edd3106b06eb4a4e",
          "md5": "186145646501129b4bdfd0f804ba96d9"
        }
      ]
    },
    {
      "name": "releaseVariantReleaseRuntimePublication",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.libraryelements": "aar",
        "org.gradle.usage": "java-runtime"
      },
      "dependencies": [
        {
          "group": "androidx.activity",
          "module": "activity",
          "version": {
            "requires": "1.5.0-alpha03"
          }
        },
        {
          "group": "androidx.core",
          "module": "core-ktx",
          "version": {
            "requires": "1.1.0"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "androidx.lifecycle",
          "module": "lifecycle-runtime-ktx",
          "version": {
            "requires": "2.5.0-alpha03"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "androidx.lifecycle",
          "module": "lifecycle-viewmodel-ktx",
          "version": {
            "requires": "2.5.0-alpha03"
          }
        },
        {
          "group": "androidx.savedstate",
          "module": "savedstate-ktx",
          "version": {
            "requires": "1.2.0-alpha01"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib",
          "version": {
            "requires": "1.6.10"
          }
        }
      ],
      "files": [
        {
          "name": "activity-ktx-1.5.0-alpha03.aar",
          "url": "activity-ktx-1.5.0-alpha03.aar",
          "size": 31645,
          "sha512": "d4b175f956cd329698705ab7ecdb080c6668d689bf9ae99e8d7c53baa4383848af73c65e280baabb4938121d5d06367a900b5fc9c072eb29aa86e89b6f0c4595",
          "sha256": "e30b007d69f63a2a0c56b5275faea7badf0f80a06caa1c50b2eba7129581793e",
          "sha1": "9818a50c9ed22d6c089026f4edd3106b06eb4a4e",
          "md5": "186145646501129b4bdfd0f804ba96d9"
        }
      ]
    },
    {
      "name": "sourcesElements",
      "attributes": {
        "org.gradle.category": "documentation",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.docstype": "sources",
        "org.gradle.usage": "java-runtime"
      },
      "files": [
        {
          "name": "activity-ktx-1.5.0-alpha03-sources.jar",
          "url": "activity-ktx-1.5.0-alpha03-sources.jar",
          "size": 7897,
          "sha512": "c484c2a29fdd1896cbdc3613c660eb83acfd8371a800eb8950783a6011623011a336cf2c9c3258119c1f22cb5ea6d9a1513125284cc3be9064a61a38afd0dd30",
          "sha256": "a66e48c18dda88d8d94f19b4250067f834d9db01ca8390c26e4530bfd2ad015e",
          "sha1": "cc99180305811c77b3fe5e10bfd099e8637bec44",
          "md5": "81c0906fb7e820a6ff164add91827fe4"
        }
      ]
    }
  ]
}
        """.trimIndent()

        // Expect that elements in "dependencies" are sorted alphabetically.
        val expected = """
{
  "formatVersion": "1.1",
  "component": {
    "group": "androidx.activity",
    "module": "activity-ktx",
    "version": "1.5.0-alpha03",
    "attributes": {
      "org.gradle.status": "release"
    }
  },
  "createdBy": {
    "gradle": {
      "version": "7.4"
    }
  },
  "variants": [
    {
      "name": "releaseVariantReleaseApiPublication",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.libraryelements": "aar",
        "org.gradle.usage": "java-api"
      },
      "dependencies": [
        {
          "group": "androidx.activity",
          "module": "activity",
          "version": {
            "requires": "1.5.0-alpha03"
          }
        },
        {
          "group": "androidx.core",
          "module": "core-ktx",
          "version": {
            "requires": "1.1.0"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "androidx.lifecycle",
          "module": "lifecycle-runtime-ktx",
          "version": {
            "requires": "2.5.0-alpha03"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "androidx.lifecycle",
          "module": "lifecycle-viewmodel-ktx",
          "version": {
            "requires": "2.5.0-alpha03"
          }
        },
        {
          "group": "androidx.savedstate",
          "module": "savedstate-ktx",
          "version": {
            "requires": "1.2.0-alpha01"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib",
          "version": {
            "requires": "1.6.10"
          }
        }
      ],
      "files": [
        {
          "name": "activity-ktx-1.5.0-alpha03.aar",
          "url": "activity-ktx-1.5.0-alpha03.aar",
          "size": 31645,
          "sha512": "d4b175f956cd329698705ab7ecdb080c6668d689bf9ae99e8d7c53baa4383848af73c65e280baabb4938121d5d06367a900b5fc9c072eb29aa86e89b6f0c4595",
          "sha256": "e30b007d69f63a2a0c56b5275faea7badf0f80a06caa1c50b2eba7129581793e",
          "sha1": "9818a50c9ed22d6c089026f4edd3106b06eb4a4e",
          "md5": "186145646501129b4bdfd0f804ba96d9"
        }
      ]
    },
    {
      "name": "releaseVariantReleaseRuntimePublication",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.libraryelements": "aar",
        "org.gradle.usage": "java-runtime"
      },
      "dependencies": [
        {
          "group": "androidx.activity",
          "module": "activity",
          "version": {
            "requires": "1.5.0-alpha03"
          }
        },
        {
          "group": "androidx.core",
          "module": "core-ktx",
          "version": {
            "requires": "1.1.0"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "androidx.lifecycle",
          "module": "lifecycle-runtime-ktx",
          "version": {
            "requires": "2.5.0-alpha03"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "androidx.lifecycle",
          "module": "lifecycle-viewmodel-ktx",
          "version": {
            "requires": "2.5.0-alpha03"
          }
        },
        {
          "group": "androidx.savedstate",
          "module": "savedstate-ktx",
          "version": {
            "requires": "1.2.0-alpha01"
          },
          "reason": "Mirror activity dependency graph for -ktx artifacts"
        },
        {
          "group": "org.jetbrains.kotlin",
          "module": "kotlin-stdlib",
          "version": {
            "requires": "1.6.10"
          }
        }
      ],
      "files": [
        {
          "name": "activity-ktx-1.5.0-alpha03.aar",
          "url": "activity-ktx-1.5.0-alpha03.aar",
          "size": 31645,
          "sha512": "d4b175f956cd329698705ab7ecdb080c6668d689bf9ae99e8d7c53baa4383848af73c65e280baabb4938121d5d06367a900b5fc9c072eb29aa86e89b6f0c4595",
          "sha256": "e30b007d69f63a2a0c56b5275faea7badf0f80a06caa1c50b2eba7129581793e",
          "sha1": "9818a50c9ed22d6c089026f4edd3106b06eb4a4e",
          "md5": "186145646501129b4bdfd0f804ba96d9"
        }
      ]
    },
    {
      "name": "sourcesElements",
      "attributes": {
        "org.gradle.category": "documentation",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.docstype": "sources",
        "org.gradle.usage": "java-runtime"
      },
      "files": [
        {
          "name": "activity-ktx-1.5.0-alpha03-sources.jar",
          "url": "activity-ktx-1.5.0-alpha03-sources.jar",
          "size": 7897,
          "sha512": "c484c2a29fdd1896cbdc3613c660eb83acfd8371a800eb8950783a6011623011a336cf2c9c3258119c1f22cb5ea6d9a1513125284cc3be9064a61a38afd0dd30",
          "sha256": "a66e48c18dda88d8d94f19b4250067f834d9db01ca8390c26e4530bfd2ad015e",
          "sha1": "cc99180305811c77b3fe5e10bfd099e8637bec44",
          "md5": "81c0906fb7e820a6ff164add91827fe4"
        }
      ]
    }
  ]
}
        """.trimIndent()
        /* ktlint-enable max-line-length */

        val actual = sortGradleMetadataDependencies(metadata)
        assertEquals(expected, actual)
    }
}
