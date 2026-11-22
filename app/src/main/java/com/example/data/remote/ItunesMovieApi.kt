package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesMovieApi {
  @GET("us/rss/topmovies/limit=25/json")
  suspend fun getTopMovies(): TopMoviesRssResponse

  @GET("search")
  suspend fun searchMovies(
    @Query("term") term: String,
    @Query("media") media: String = "movie",
    @Query("entity") entity: String = "movie",
    @Query("limit") limit: Int = 25,
    @Query("country") country: String = "us",
  ): ItunesSearchResponse

  @GET("lookup")
  suspend fun lookupMovie(
    @Query("id") id: Long,
    @Query("entity") entity: String = "movie",
    @Query("country") country: String = "us",
  ): ItunesSearchResponse
}
