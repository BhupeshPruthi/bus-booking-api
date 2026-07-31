package com.mybus.app.data.remote

import com.mybus.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PATCH
import retrofit2.http.Query

interface ApiService {

    @POST("auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequest): Response<ApiResponse<LoginData>>

    @POST("auth/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<ApiResponse<TokenData>>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshTokenRequest): Response<ApiResponse<LogoutData>>

    @POST("admin/trips")
    suspend fun createTrip(@Body request: CreateTripRequest): Response<ApiResponse<TripData>>

    @POST("admin/poojas")
    suspend fun createPooja(@Body request: CreatePoojaRequest): Response<ApiResponse<PoojaDetailData>>

    @GET("poojas")
    suspend fun getUpcomingPoojas(): Response<ApiResponse<List<PoojaListItem>>>

    @GET("poojas/{id}")
    suspend fun getPoojaDetail(@Path("id") poojaId: String): Response<ApiResponse<PoojaDetailData>>

    @POST("admin/events")
    suspend fun createEvent(@Body request: CreateEventRequest): Response<ApiResponse<EventListItem>>

    @Multipart
    @POST("admin/events")
    suspend fun createEventMultipart(
        @Part("header") header: RequestBody,
        @Part("subHeader") subHeader: RequestBody,
        @Part("eventDate") eventDate: RequestBody,
        @Part image: MultipartBody.Part? = null
    ): Response<ApiResponse<EventListItem>>

    @GET("events")
    suspend fun getUpcomingEvents(): Response<ApiResponse<List<EventListItem>>>

    @POST("poojas/{id}/bookings")
    suspend fun bookPoojaToken(
        @Path("id") poojaId: String,
        @Body request: BookPoojaRequest
    ): Response<ApiResponse<PoojaBookingData>>

    @GET("poojas/bookings")
    suspend fun getMyPoojaBookings(): Response<ApiResponse<List<PoojaTokenHistoryItem>>>

    @GET("admin/poojas")
    suspend fun getAdminPoojas(): Response<ApiResponse<List<PoojaListItem>>>

    @GET("admin/poojas/{id}")
    suspend fun getAdminPoojaDetail(@Path("id") poojaId: String): Response<ApiResponse<PoojaDetailData>>

    @POST("admin/poojas/{poojaId}/bookings/{bookingId}/cancel")
    suspend fun cancelPoojaBookingAsAdmin(
        @Path("poojaId") poojaId: String,
        @Path("bookingId") bookingId: String
    ): Response<ApiResponse<PoojaBookingData>>

    @GET("buses")
    suspend fun getBuses(
        @Query("source") source: String? = null,
        @Query("destination") destination: String? = null,
        @Query("date") date: String? = null
    ): Response<ApiResponse<List<BusListItem>>>

    @GET("admin/buses")
    suspend fun getAdminBuses(
        @Query("status") status: String? = null
    ): Response<ApiResponse<List<BusListItem>>>

    @DELETE("admin/buses/{id}")
    suspend fun deleteBus(@Path("id") busId: String): Response<ApiResponse<DeleteBusResult>>

    @GET("buses/{id}")
    suspend fun getBusDetail(@Path("id") busId: String): Response<ApiResponse<BusDetailData>>

    @POST("bookings")
    suspend fun createBooking(@Body request: CreateBookingRequest): Response<ApiResponse<BookingData>>

    @GET("bookings")
    suspend fun getMyBookings(
        @Query("status") status: String? = null
    ): Response<ApiResponse<List<BookingData>>>

    @GET("me/bookings")
    suspend fun getUnifiedBookings(
        @Query("bucket") bucket: String,
        @Query("types") types: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<UnifiedBookingPage>>

    @GET("me/bookings/{bookingType}/{bookingId}")
    suspend fun getUnifiedBooking(
        @Path("bookingType") bookingType: String,
        @Path("bookingId") bookingId: String
    ): Response<ApiResponse<UnifiedBookingItem>>

    @GET("bookings/{id}")
    suspend fun getBookingById(@Path("id") bookingId: String): Response<ApiResponse<BookingData>>

    @POST("bookings/{id}/cancellation-request")
    suspend fun requestBookingCancellation(
        @Path("id") bookingId: String,
        @Body request: BookingCancellationRequest = BookingCancellationRequest()
    ): Response<ApiResponse<BookingData>>

    @GET("admin/bookings")
    suspend fun getAdminBookings(
        @Query("busId") busId: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<List<BookingData>>>

    @PUT("admin/bookings/{id}/status")
    suspend fun updateBookingStatus(
        @Path("id") bookingId: String,
        @Body request: UpdateBookingStatusRequest
    ): Response<ApiResponse<BookingData>>

    @POST("admin/bookings/{id}/cancel")
    suspend fun cancelBookingAsAdmin(
        @Path("id") bookingId: String,
        @Body request: BookingCancellationRequest = BookingCancellationRequest()
    ): Response<ApiResponse<BookingData>>

    @GET("stays/catalog")
    suspend fun getStayCatalog(): Response<ApiResponse<StayCatalog>>

    @POST("stays/quote")
    suspend fun getStayQuote(@Body request: StayQuoteRequest): Response<ApiResponse<StayQuote>>

    @POST("stays/bookings")
    suspend fun createStayBooking(
        @Body request: CreateStayBookingRequest
    ): Response<ApiResponse<StayBooking>>

    @GET("stays/bookings")
    suspend fun getMyStayBookings(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<StayPage<StayBooking>>>

    @GET("stays/bookings/{id}")
    suspend fun getStayBooking(@Path("id") id: String): Response<ApiResponse<StayBooking>>

    @POST("stays/bookings/{id}/cancellation-requests")
    suspend fun requestStayCancellation(
        @Path("id") id: String,
        @Body request: StayCancellationRequest
    ): Response<ApiResponse<StayCancellation>>

    @GET("admin/stay/bookings")
    suspend fun getAdminStayBookings(
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<StayPage<StayBooking>>>

    @POST("admin/stay/bookings/{id}/confirm")
    suspend fun confirmStayBooking(@Path("id") id: String): Response<ApiResponse<StayBooking>>

    @POST("admin/stay/bookings/{id}/reject")
    suspend fun rejectStayBooking(
        @Path("id") id: String,
        @Body request: StayRejectionRequest
    ): Response<ApiResponse<StayBooking>>

    @GET("admin/stay/cancellation-requests")
    suspend fun getStayCancellationRequests(
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<StayPage<StayCancellation>>>

    @POST("admin/stay/cancellation-requests/{id}/decision")
    suspend fun decideStayCancellation(
        @Path("id") id: String,
        @Body request: StayCancellationDecisionRequest
    ): Response<ApiResponse<StayBooking>>

    @PATCH("admin/stay/unit-types/{id}")
    suspend fun updateStayUnitType(
        @Path("id") id: String,
        @Body request: UpdateStayUnitTypeRequest
    ): Response<ApiResponse<StayUnitType>>

    @GET("admin/admin-users")
    suspend fun getAdminUsers(
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<StayPage<AdminUser>>>

    @PATCH("admin/admin-users/{id}")
    suspend fun updateAdminType(
        @Path("id") id: String,
        @Body request: UpdateAdminTypeRequest
    ): Response<ApiResponse<AdminUser>>
}
