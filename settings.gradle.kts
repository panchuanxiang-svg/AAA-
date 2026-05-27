@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()

        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://maven.hq.hydraulic.software")
//        maven("file:libs/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()

        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap/")
        maven("https://jitpack.io")
//        maven("file:libs/")
        maven("https://repo.jenkins-ci.org/public/")
    }
}

rootProject.name = "SamloaderKotlin"
include(":android")
include(":desktop")
include(":common")

                region.isNotBlank() &&&
                !hasRunningJobs

    val canDownload =
        modelModel.isNotBlank() &&
                region.isNotBlank() &&
                fw.isNotBlank() &&
                !hasRunningJobs

    val canChangeOption = !hasRunningJobs

    val scope = rememberCoroutineScope()

    var downloadErrorInfo by remember {
        mutableStateOf<Downloader.DownloadErrorInfo?>(null)
    }

    val scrollState = rememberScrollState()

    ColumnScrollbar(
        state = scrollState,
        settings = ThemeConstants.ScrollBarSettings.Default,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .verticalScroll(scrollState),
        ) {

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 8.dp,
                        top = LocalMenuBarHeight.current
                    ),
            ) {

                val constraints = constraints

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    HybridButton(
                        onClick = {
                            model.launchJob {
                                Downloader.onDownload(
                                    model,
                                    confirmCallback = object :
                                        Downloader.DownloadErrorCallback {

                                        override fun onError(
                                            info: Downloader.DownloadErrorInfo
                                        ) {
                                            downloadErrorInfo = info
                                        }
                                    },
                                )
                            }
                        },
                        enabled = canDownload,
                        vectorIcon = painterResource(MR.images.download),
                        text = stringResource(MR.strings.download),
                        description = stringResource(MR.strings.downloadFirmware),
                        parentSize = constraints.maxWidth
                    )

                    Spacer(Modifier.width(8.dp))

                    HybridButton(
                        onClick = {
                            model.launchJob {

                                Downloader.onFetch(
                                    model = model,
                                    betaMode = betaMode,
                                    incrementalMode = incrementalMode,
                                )
                            }
                        },
                        enabled = canCheckVersion,
                        text = stringResource(MR.strings.checkForUpdates),
                        vectorIcon = painterResource(MR.images.refresh),
                        description = stringResource(MR.strings.checkForUpdatesDesc),
                        parentSize = constraints.maxWidth,
                    )

                    Spacer(Modifier.weight(1f))

                    HybridButton(
                        onClick = {
                            scope.launch {
                                eventManager.sendEvent(Event.Download.Finish)
                            }

                            model.endJob("")
                        },
                        enabled = hasRunningJobs,
                        text = stringResource(MR.strings.cancel),
                        description = stringResource(MR.strings.cancel),
                        vectorIcon = painterResource(MR.images.cancel),
                        parentSize = constraints.maxWidth
                    )
                }
            }

            val boxSource = remember {
                MutableInteractionSource()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .animateContentSize()
                    .padding(bottom = 8.dp),
            ) {

                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = boxSource,
                            indication = null,
                            enabled = canChangeOption,
                        ) {
                            manual = !manual
                        }
                        .padding(4.dp)
                ) {

                    Checkbox(
                        checked = manual,
                        onCheckedChange = {
                            manual = it
                        },
                        modifier = Modifier.align(Alignment.CenterVertically),
                        enabled = canChangeOption,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                        interactionSource = boxSource
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = stringResource(MR.strings.manual),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            // ===== 新增 Beta UI =====

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {

                Checkbox(
                    checked = betaMode,
                    onCheckedChange = {
                        betaMode = it

                        if (it) {
                            incrementalMode = false
                        }
                    }
                )

                Text(
                    text = "大版本 Beta 测试",
                    modifier = Modifier.padding(end = 16.dp)
                )

                Checkbox(
                    checked = incrementalMode,
                    onCheckedChange = {
                        incrementalMode = it

                        if (it) {
                            betaMode = false
                        }
                    }
                )

                Text(
                    text = "日常更新 / 小版本测试"
                )
            }

            AnimatedVisibility(
                visible = modelModel.isAccessoryModel,
                enter = fadeIn() + expandIn(expandFrom = Alignment.CenterStart),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.CenterStart),
            ) {

                Text(
                    text = stringResource(MR.strings.accessory_model_warning),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            MRFLayout(
                model,
                canChangeOption,
                manual && canChangeOption,
            )

            AnimatedVisibility(
                visible = !manual && osCode.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {

                val displayCode = remember {
                    osCode
                }

                Column {

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = stringResource(
                            MR.strings.osVersion,
                            displayCode
                        ),
                    )
                }
            }

            AnimatedVisibility(
                visible =
                hasRunningJobs ||
                        progress.first > 0 ||
                        progress.second > 0 ||
                        statusText.isNotBlank(),
            ) {

                Column {

                    Spacer(modifier = Modifier.size(4.dp))

                    ProgressInfo(model)
                }
            }

            val changelogCondition =
                changelog != null &&
                        !manual &&
                        !hasRunningJobs &&
                        fw.isNotBlank()

            AnimatedVisibility(
                visible = changelogCondition,
            ) {

                ExpandButton(
                    changelogExpanded,
                    stringResource(MR.strings.changelog),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    changelogExpanded = it
                }
            }

            AnimatedVisibility(
                visible = changelogExpanded && changelogCondition,
                modifier = Modifier.fillMaxWidth(),
            ) {

                ChangelogDisplay(changelog)
            }
        }
    }

    InWindowAlertDialog(
        showing = downloadErrorInfo != null,
        onDismissRequest = {

            model.launchJob {

                downloadErrorInfo?.callback?.onCancel?.invoke()

                downloadErrorInfo = null
            }
        },
        title = {
            Text(text = stringResource(MR.strings.warning))
        },
        text = {
            Text(text = downloadErrorInfo?.message ?: "")
        },
        buttons = {

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = {

                    model.launchJob {

                        val info = downloadErrorInfo

                        downloadErrorInfo = null

                        info?.callback?.onCancel?.invoke()
                    }
                }
            ) {

                Text(text = stringResource(MR.strings.no))
            }

            TextButton(
                onClick = {

                    model.launchJob {

                        val info = downloadErrorInfo

                        downloadErrorInfo = null

                        info?.callback?.onAccept?.invoke()
                    }
                }
            ) {

                Text(
                    text = stringResource(MR.strings.yes),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}
