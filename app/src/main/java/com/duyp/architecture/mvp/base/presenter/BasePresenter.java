package com.duyp.architecture.mvp.base.presenter;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.duyp.androidutils.CustomSharedPreferences;
import com.duyp.androidutils.rx.functions.PlainConsumer;
import com.duyp.architecture.mvp.base.BaseView;
import com.duyp.architecture.mvp.base.interfaces.Lifecycle;
import com.duyp.architecture.mvp.base.interfaces.Refreshable;
import com.duyp.architecture.mvp.dagger.qualifier.ActivityContext;
import com.duyp.architecture.mvp.data.Resource;
import com.duyp.architecture.mvp.data.Status;
import com.duyp.architecture.mvp.data.local.user.UserManager;
import com.duyp.architecture.mvp.data.local.user.UserDataStore;
import com.duyp.architecture.mvp.data.model.base.ErrorEntity;
import com.duyp.architecture.mvp.data.remote.GithubService;
import com.duyp.architecture.mvp.utils.api.ApiUtils;

import org.greenrobot.eventbus.EventBus;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import lombok.Getter;
import retrofit2.Response;

@Getter
public abstract class BasePresenter<V extends BaseView> implements Lifecycle, Refreshable {

    @Nullable
    private V mView;

    protected Context context;

    private UserManager userManager;

    private EventBus eventBus;

    @NonNull
    private CompositeDisposable mCompositeDisposable;

    public BasePresenter(@ActivityContext Context context, UserManager userManager){
        this.context = context;
        this.userManager = userManager;
        eventBus = EventBus.getDefault();
        mCompositeDisposable = new CompositeDisposable();
    }

    /**
     * Called when view handled by this presenter is available.
     * It will be called no later than Timeline/Fragment onStart() method call.
     *
     * @param view Object representing MVP view layer
     */
    public void bindView(V view) {
        this.mView = view;
    }

    /**
     * Called when view is being unbind from presenter component.
     * It will be called no later than Timeline/Fragment onStop() method call.
     */
    public void unbindView() {
        this.mView = null;
    }

    /**
     * @return View layer
     */
    public V getView() {
        return mView;
    }

    /**
     * @return {@link LifecycleOwner} associate with this presenter (host activities, fragments)
     */
    protected LifecycleOwner getLifeCircleOwner() {
        return (LifecycleOwner) mView;
    }

    public GithubService getGithubService() {
        return userManager.getGithubService();
    }

    public UserDataStore getUserRepo() {
        return userManager.getUserRepo();
    }

    public CustomSharedPreferences getSharedPreference() {
        return getUserRepo().getSharedPreferences();
    }

    @Override
    public void refresh() {

    }


    /**
     * add a request with {@link Resource} flowable created by
     * {@link com.duyp.architecture.mvp.base.data.BaseRepo#createResource(Single, PlainConsumer)}
     * @param showProgress
     * @param resourceFlowable
     * @param response
     * @param <T>
     */
    public <T> void addRequest(boolean showProgress, Flowable<Resource<T>> resourceFlowable, @Nullable PlainConsumer<T> response) {
        Disposable disposable = resourceFlowable.observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.newThread())
                .subscribe(resource -> {
                    if (resource != null && getView() != null) {
                        Log.d("source", "addRequest: resource changed: " + resource.toString());
                        if (resource.data != null && response != null) {
                            response.accept(resource.data);
                        }
                        if (showProgress) {
                            getView().setProgress(resource.status == Status.LOADING);
                        }
                        if (resource.message != null) {
                            getView().showMessage(resource.message);
                        }
                    }
                });
        if (mCompositeDisposable.isDisposed()) {
            mCompositeDisposable = new CompositeDisposable();
        }
        mCompositeDisposable.add(disposable);
    }

    public <T> void addRequest(Flowable<Resource<T>> resourceFlowable, PlainConsumer<T> response) {
        addRequest(true, resourceFlowable, response);
    }

    public <T> void addRequest(Flowable<Resource<T>> resourceFlowable) {
        addRequest(true, resourceFlowable, null);
    }

    public <T> void addRequest(boolean showProgress, Flowable<Resource<T>> resourceFlowable) {
        addRequest(showProgress, resourceFlowable, null);
    }

    /**
     * NULL SAFE
     * Add new api request to {@link CompositeDisposable} and execute immediately
     * All error case and progress showing will be handled automatically
     * @param request           observable request
     * @param showProgress      true if should show loading progress
     *
     * @param responseConsumer   callback for success response.
     * @param errorConsumer     callback for error case.
     *                          If both of these listeners are null, the request will be subscribed
     *                          on io thread without observing on main thread
     *                          * no update UI in case of both success and error are null
     * @param forceResponseWithoutCheckNullView the success result will be returned without check null for view
     * @param <T> Type of response body
     */
    protected <T> void addRequest(
            Single<Response<T>> request, boolean showProgress,
            boolean forceResponseWithoutCheckNullView,
            @Nullable PlainConsumer<T> responseConsumer,
            @Nullable PlainConsumer<ErrorEntity> errorConsumer) {

        boolean shouldUpdateUI = showProgress || responseConsumer != null || errorConsumer != null;

        if (showProgress && mView != null) {
            mView.showProgress();
        }

        Disposable disposable = ApiUtils.makeRequest(request, shouldUpdateUI, response -> {
            if (responseConsumer != null && (forceResponseWithoutCheckNullView || mView != null)) {
                responseConsumer.accept(response);
            }
        }, error -> {
            if (errorConsumer != null) {
                errorConsumer.accept(error);
            } else if (mView != null) {
                mView.onError(error);
            }
        }, () -> {
            // complete
            if (showProgress && mView != null) {
                mView.hideProgress();
            }
        });

        if (mCompositeDisposable.isDisposed()) {
            mCompositeDisposable = new CompositeDisposable();
        }
        mCompositeDisposable.add(disposable);
    }

    /**
     * Add a request without handling error and no update UI
     */
    protected <T> void addRequest(Single<Response<T>> request) {
        addRequest(request, false, false, null, null);
    }

    /**
     * Add a request with success listener
     */
    protected <T> void addRequest(Single<Response<T>> request, boolean showProgress,
                                  @Nullable PlainConsumer<T> responseConsumer) {
        addRequest(request, showProgress, false, responseConsumer, null);
    }

    /**
     * Add a request with success listener and error listener
     */
    protected <T> void addRequest(Single<Response<T>> request, boolean showProgress,
                                                     @Nullable PlainConsumer<T> responseConsumer,
                                                     @Nullable PlainConsumer<ErrorEntity> errorListener) {
        addRequest(request, showProgress, false, responseConsumer, errorListener);
    }

    /**
     * Add a request with success listener and forceResponseWithoutCheckNullView param
     */
    protected <T> void addRequest(Single<Response<T>> request, boolean showProgress,
                                                     boolean forceResponseWithoutCheckNullView,
                                                     @Nullable PlainConsumer<T> responseConsumer) {
        addRequest(request, showProgress, forceResponseWithoutCheckNullView, responseConsumer, null);
    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {

    }

    @Override
    public void onStart() {

    }

    @Override
    public void onStop() {

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {

    }

    /**
     * Dispose all subscribed subscriptions
     */
    @Override
    public void onDestroy() {
        mCompositeDisposable.dispose();
    }
}
