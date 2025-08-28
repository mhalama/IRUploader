package cz.zorn.iruploader.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import cz.zorn.iruploader.HexLoader
import cz.zorn.iruploader.HexLoaderImpl
import cz.zorn.iruploader.IRFirmwareSender
import cz.zorn.iruploader.IRFirmwareSenderImpl
import cz.zorn.iruploader.IRMessageSender
import cz.zorn.iruploader.IRMessageSenderImpl
import cz.zorn.iruploader.MainActivityVM
import cz.zorn.iruploader.SocketServer
import cz.zorn.iruploader.SocketServerImpl
import cz.zorn.iruploader.UploaderRepository
import cz.zorn.iruploader.UploaderRepositoryImpl
import cz.zorn.iruploader.db.FirmwareDao
import cz.zorn.iruploader.db.FirmwareDaoImpl
import cz.zorn.iruploader.db.FirmwareDatabase
import cz.zorn.iruploader.db.MessageDao
import cz.zorn.iruploader.db.MessageDaoImpl
import cz.zorn.iruploader.irotg.IROTG
import cz.zorn.iruploader.irotg.IROTGImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<FirmwareDatabase> {
        // androidContext().deleteDatabase("firmware.db")
        val driver = AndroidSqliteDriver(FirmwareDatabase.Schema, get(), "firmware.db")
        FirmwareDatabase(driver)
    }

    singleOf(::IROTGImpl) { bind<IROTG>() }

    singleOf(::FirmwareDaoImpl) { bind<FirmwareDao>() }
    singleOf(::MessageDaoImpl) { bind<MessageDao>() }

    single<Job> { SupervisorJob() }
    single<CoroutineScope> { CoroutineScope(Dispatchers.Default + get<Job>()) }
    single<SocketServer> {
        SocketServerImpl(
            port = 12345,
            scope = get(),
            repository = get(),
        )
    }

    singleOf(::HexLoaderImpl) { bind<HexLoader>() }
    singleOf(::IRFirmwareSenderImpl) { bind<IRFirmwareSender>() }
    singleOf(::IRMessageSenderImpl) { bind<IRMessageSender>() }
    singleOf(::UploaderRepositoryImpl) { bind<UploaderRepository>() }

    viewModelOf(::MainActivityVM)
}