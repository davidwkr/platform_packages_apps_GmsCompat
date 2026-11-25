package app.grapheneos.gmscompat.lib.providerinstaller;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.BinderWrapper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import app.grapheneos.gmscompat.lib.util.ServiceConnectionWrapper;

public final class ProviderInstallerCompat {
    private static final String TAG = "GmcProviderInstaller";

    public static ServiceConnection maybeWrap(Context host, Intent intent, UserHandle user,
            ServiceConnection original) {
        if (host == null || intent == null || original == null
                || host.getApplicationInfo().uid != Process.myUid()
                || !Process.myUserHandle().equals(user)
                || PhenotypeRequest.GMS.equals(host.getPackageName())
                || !PhenotypeRequest.GMS.equals(intent.getPackage())
                || !"com.google.android.gms.phenotype.service.START".equals(intent.getAction())) {
            return null;
        }
        String hostPackage = host.getPackageName();
        Log.i(TAG, "wrapping Phenotype connection for host=" + hostPackage);
        return new ServiceConnectionWrapper(original, binder -> {
            try {
                if (!PhenotypeRequest.DESCRIPTOR.equals(binder.getInterfaceDescriptor())) return null;
            } catch (RemoteException e) {
                return null;
            }
            AtomicBoolean loggedRewrite = new AtomicBoolean();
            AtomicBoolean loggedPassthrough = new AtomicBoolean();
            return new BinderWrapper(binder) {
                @Override
                public boolean transact(int code, Parcel data, Parcel reply, int flags)
                        throws RemoteException {
                    Parcel replacement = code == 46 ? PhenotypeRequest.rewrite(data, hostPackage) : null;
                    if (code == 46) {
                        AtomicBoolean marker = replacement != null ? loggedRewrite : loggedPassthrough;
                        if (marker.compareAndSet(false, true)) {
                            Log.i(TAG, "broker transaction 46 rewrite=" + (replacement != null)
                                    + " host=" + hostPackage);
                        }
                    }
                    if (replacement == null) return super.transact(code, data, reply, flags);
                    try {
                        return super.transact(code, replacement, reply, flags);
                    } finally {
                        replacement.recycle();
                    }
                }
            };
        });
    }

    private ProviderInstallerCompat() {}
}
