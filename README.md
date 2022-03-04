# OH-CMakeGenerator

## 1. 背景

相信很多用惯了 IDE 的童鞋下载了 OpenHarmony 代码后，都会比较苦恼没有一个趁手的工具去查看代码，包括代码高亮、跳转等等。虽然官方提供了基于 VSCode 的开发工具，使用下来，还是没有 Android Studio 那么随心所欲，为了解决这个问题，我们开发了一个工具，通过这个工具，可以使源码导入 Clion（墙裂推荐，用过 Android Studio 的童鞋几乎可以无缝切换使用）、Eclipse 等IDE，下面是使用 Clion 浏览代码时的效果，还算丝滑吧~

![](http://image.hanschen.site/master/2019-10-11-15-02-40.gif)

# 2. 实现思路

由于 `OpenHarmony` 代码编译后，会在 `out/rk3568/obj` 目录下生成每个模块的 `ninja` 文件，对 `ninja` 文件进行解析即可获得每个模块依赖的：

- 头文件路径
- 源文件路径
- 编译时设置的宏

有了这些信息，只要把它转换成 `CMake` 文件，即可方便的导入各大支持 `CMake` 的 `IDE`

# 3. 使用说明

1. 下载工具：[CMakeGenerator](./distributions/CMakeGenerator-0.1.0.zip)，并进行解压
2. 对 `OpenHarmony` 进行编译
3. 执行 `CMake` 生成命令：

```bash
./CMakeGenerator-0.1.0/bin/CMakeGenerator --sourceDir /home/chenhang/ssd/OpenHarmony -p rk3568

Generate start
sourceDir   : /home/chenhang/ssd/OpenHarmony
cmakeDir    : /home/chenhang/ssd/OpenHarmony/cmake
productName : rk3568

process: /home/chenhang/ssd/OpenHarmony/out/rk3568/obj/ark/runtime_core/libpandabase/libarkbase_frontend_static.ninja
process: /home/chenhang/ssd/OpenHarmony/out/rk3568/obj/ark/runtime_core/libpandabase/libarkbase.ninja
...

Successful : /home/chenhang/ssd/OpenHarmony/cmake
```

支持参数有：

- `--sourceDir`: 可选，指定 `OpenHarmony` 源码根目录，默认当前工作目录
- `--productName`：必选，指定当前编译的 `product` 名
- `--cmakeDir`： 可选，`CMake` 文件生成的路径，默认为 `sourceDir/cmake`


## 4. 导入 `IDE`

只要生成 `CMake` 文件后，剩下的事情就好办了，现在能识别 `CMake` 工程的 `IDE` 非常多，大家可以根据个人喜好选择，如：

- CLion
- Eclipse
- Visual Studio

这里以 CLion 为例讲一下如何导入：


- 打开 CLion
- 选择「Open」
- 选择 cmakeDir 目录下的 CMakeLists.txt ，并选择「Open as Project」
- CMakeLists.txt 中放开你想查看的模块代码的注释（模块太多了，默认不添加）
- 在「ToolS」-「CMake」-「Reload CMake Project」
- Enjoy your journey …

## 5. 补充建议

- Clion 是收费的，我们要支持正版，不过...这里提供一个方法 [免费使用](https://jetbra.in/78a45275-eef9-4b6d-b530-578eba6d1050.html)
- 在「ToolS」-「CMake」-「Change Project Root」 中修改为 OpenHarmony 的源码路径（注意不是生成的 CMake 的根路径），这样搜索才比较方便
- 修改 `Project Root` 后，在左侧资源管理器中，对不经常查看的模块 `Exclude` 掉，能提升 IDE 速度，方法：选择目录，右键-「Mark Directory as」-「Excluded」


---

# License

Apache 2.0. See the [LICENSE](./LICENSE) file for details.
