package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;

// Interface này nằm ở Client, nhưng Server sẽ gọi nó!
public interface ClientCallback extends Remote {
    // Hàm này sẽ được Server kích hoạt khi có ai đó online/offline/đổi IP
    void onFriendStatusChange(UserDTO friend) throws RemoteException;
}