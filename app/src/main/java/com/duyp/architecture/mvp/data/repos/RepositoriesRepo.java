package com.duyp.architecture.mvp.data.repos;

import android.arch.lifecycle.LifecycleOwner;
import android.support.annotation.Nullable;
import android.util.Log;

import com.duyp.architecture.mvp.base.data.BaseRepo;
import com.duyp.architecture.mvp.base.data.LiveRealmResults;
import com.duyp.architecture.mvp.data.Resource;
import com.duyp.architecture.mvp.data.local.RealmDatabase;
import com.duyp.architecture.mvp.data.local.dao.RepositoryDao;
import com.duyp.architecture.mvp.data.local.user.UserDataStore;
import com.duyp.architecture.mvp.data.model.Repository;
import com.duyp.architecture.mvp.data.model.User;
import com.duyp.architecture.mvp.data.model.def.RepoTypes;
import com.duyp.architecture.mvp.data.remote.GithubService;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.Single;
import lombok.Getter;
import retrofit2.Response;

import static com.duyp.architecture.mvp.data.SimpleNetworkBoundSourceLiveData.TAG;
/**
 * Created by duypham on 9/15/17.
 *
 */

public class RepositoriesRepo extends BaseRepo {

    public static final int PER_PAGE = 100;

    @Getter
    protected final RepositoryDao repositoryDao;

    @Getter
    private LiveRealmResults<Repository> data;

    private int currentPage = 1;

    @Nullable
    private final User mUser;

    @Inject
    public RepositoriesRepo(LifecycleOwner owner, GithubService githubService, RealmDatabase realmDatabase, UserDataStore userDataStore) {
        super(owner, githubService, realmDatabase);
        this.repositoryDao = realmDatabase.getRepositoryDao();
        mUser = userDataStore.getUser();
    }

    /**
     * Get all public repositories from github api
     * @param sinceId last repository item got
     * @return
     */
    public Flowable<Resource<List<Repository>>> getAllRepositories(Long sinceId) {
        Log.d(TAG, "RepositoriesRepo: getting all repo with sinceId = " + sinceId);
        if (sinceId != null) {
            currentPage ++;
        } else {
            currentPage = 1;
        }
//        data = repositoryDao.getAll(currentPage * PER_PAGE);
        data = repositoryDao.getAll();
        return createResource(getGithubService().getAllPublicRepositories(sinceId), repositoryDao::addAll);
    }

    /**
     * Find repositories by given repository name
     * @param repoName
     * @return
     */
    public Flowable<Resource<List<Repository>>> findRepositories(String repoName) {
        Log.d(TAG, "RepositoriesRepo: finding repo: " + repoName);
        data = repositoryDao.getRepositoriesWithNameLike(repoName);
        return createResource(getGithubService().getAllPublicRepositories(null), repositoryDao::addAll);
    }

    /**
     * Get repositories of given user
     * if given user is current saved user, we get his own repositories by {@link GithubService#getMyRepositories(String)}
     * @param userNameLogin user login name
     * @return
     */
    public Flowable<Resource<List<Repository>>> getUserRepositories(String userNameLogin) {
        data = repositoryDao.getUserRepositories(userNameLogin);

        boolean isOwner = mUser != null && mUser.getLogin().equals(userNameLogin);
        Single<Response<List<Repository>>> remote = isOwner ? getGithubService().getMyRepositories(RepoTypes.ALL) :
                getGithubService().getUserRepositories(userNameLogin, RepoTypes.ALL);

        return createResource(remote, repositories -> {
            if (isOwner) {
                for (Repository repository : repositories) {
                    if (!repository.getOwner().getLogin().equals(mUser.getLogin())) {
                        repository.setMemberLoginName(mUser.getLogin());
                    }
                }
            }
            repositoryDao.addAll(repositories);
        });
    }
}