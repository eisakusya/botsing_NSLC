
# Botsing

[![Build Status](https://travis-ci.org/STAMP-project/botsing.svg?branch=master)](https://travis-ci.org/STAMP-project/botsing)
[![Coverage Status](https://coveralls.io/repos/github/STAMP-project/botsing/badge.svg?branch=master)](https://coveralls.io/github/STAMP-project/botsing?branch=master)
[![Maven Central](https://img.shields.io/maven-central/v/eu.stamp-project/botsing-reproduction.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22eu.stamp-project%22%20AND%20a:%22botsing-reproduction%22)

Botsing is a Java framework for search-based crash reproduction. It implements and extends the EvoCrash approach and relies on [EvoSuite](http://www.evosuite.org) for Java code instrumentation during test generation and execution. The documentation is available on the [Botsing companion website](https://stamp-project.github.io/botsing).

## Funding

Botsing is partially funded by research project STAMP (European Commission - H2020) ICT-16-10 No.731529.

![STAMP - European Commission - H2020](docs/assets/logo_readme_md.png)

# Tutorials on IntelliJ

## 在使用IntelliJ上使用NSLC时，需要在Bosting的运行配置中配置好运行的args实参，即运行 Botsing 所需要的参数

命令由以下部分组成：

1-`-project_cp`:当前崩溃所需依赖所在的目录

2-`-crash_log`:当前崩溃的`log`文件所在位置

3-`-target_frame`:需要复现的帧数

4-`-Dsearch_budget`:搜索的时间budget，以秒做单位，当时间耗尽搜索无论是否找到都会停止

5-`-Dtest_dir`:搜索生成用例存放目录

6-`-search_algorithm`:搜索算法的选用，此处如果是跑NSLC请直接在后面跟`NoveltySearch`即可。此配置默认为单目标搜索
算法 ，其他多目标算法请自行查阅`CrashProperties`类学习使用

7-`-fitness`:适应度函数的选用，默认为`WeightedSum`。如果是跑NSLC该参数选项请忽略。其他适应度函数参阅同第六点

8-`-epsilon`:用于自定义e-dominance的epsilon参数，默认不设置为0.3

9-其他命令请查阅`"Botsing"`类进行学习使用

## 使用准备

1-请参阅数据集中的相关说明，在数据库中`crashes`表示崩溃的日志文件，请在`applications`目录中寻找对应的依赖包进行使用

2-根据`crashes`和`applications`的（绝对）目录地址，组成args实参，并放到运行配置中运行即可

## 运行结果

在运行后如果目标崩溃被复现了，相关代码和抛出异常信息会在控制台进行相关显示，可以在当处查阅，并且不会在生成的Java文件中显示

## 命令例子

`-project_cp D:/Java/botsing_home/example/applications -crash_log D:/Java/botsing_home/example/crashes/CHART-4b.log -target_frame 6 -Dsearch_budget=300 -Dtest_dir=D:/Java/botsing_home/example/results -search_algorithm NoveltySearch `

## 数据集地址

**有结果数据，来源于论文4作者的论文数据**：`https://zenodo.org/record/3979097`

**无结果数据，来源于JCrashPack**：`链接：https://pan.baidu.com/s/1dXo8piucKktM2XAiEWT4mQ
提取码：vcso`
