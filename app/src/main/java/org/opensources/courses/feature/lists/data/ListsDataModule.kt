package org.opensources.courses.feature.lists.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.opensources.courses.feature.lists.domain.ShoppingListRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class ListsDataModule {
    @Binds
    abstract fun bindShoppingListRepository(repository: ShoppingListRepositoryImpl): ShoppingListRepository
}
