package org.opensources.courses.feature.shopping.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.opensources.courses.feature.shopping.domain.ShoppingItemRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class ShoppingDataModule {
    @Binds
    abstract fun bindShoppingItemRepository(repository: ShoppingItemRepositoryImpl): ShoppingItemRepository
}
