package omar.ossim.omar.federation

import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.packet.VCard
import org.jivesoftware.smackx.provider.VCardProvider
import org.ossim.omar.federation.FederatedServer
import org.ossim.omar.federation.JabberParticipantListener
import org.springframework.beans.factory.InitializingBean

class JabberFederatedServerService implements InitializingBean{
    def grailsApplication
    def vCard
    def jabberDomain
    def jabberPort
    def jabberAdminUser
    def jabberAdminPassword
    def jabberChatRoomId
    def jabberChatRoomPassword
    def jabber
    def participantListener

    def loadFromConfig(def config)
    {
        vCard = new VCard()
        def federation = config.federation
        federation?.vcard?.fields?.each{key,value->
            switch(key.toLowerCase())
            {
                case "firstname":
                    vCard.setFirstName(value)
                    break
                case "lastname":
                    vCard.setLastName(value)
                case "nickname":
                    vCard.setNickName(value)
                case "organization":
                    vCard.setOrganization(value)
                default:
                    vCard.setField(key, value)
                    break
            }
        }
        vCard.setField("IP", config?.omar?.serverIP)
        vCard.setField("URL", config?.omar?.serverURL)
        if (!vCard.getNickName())
        {
            vCard.setNickName("${config?.omar?.serverIP}");
        }

        jabberDomain        = federation?.server?.ip
        jabberPort          = federation?.server?.port
        jabberAdminUser     = federation?.server?.username
        jabberAdminPassword = federation?.server?.password
        jabberChatRoomId    = federation?.chatRoom?.id
        jabberChatRoomPassword    = federation?.chatRoom?.password
        vCard.setJabberId("${config?.omar?.serverIP}@${jabberDomain}")
    }
    def isConnected()
    {
        jabber?.connection?.isConnected()
    }
    def refreshServerTable()
    {
        def vcardList = getAllVCards()

        FederatedServer.withTransaction {
            FederatedServer.executeUpdate('delete from FederatedServer')
            // write our out first all the time so we are first in the list
            def federatedServer = new FederatedServer(
                    [serverId:vCard.getField("IP")+"@${jabberDomain}",//"${application.config.omar.serverURL}",
                            available:true,
                            vcard: vCard.toString()
                    ])
            try{
                federatedServer.save()
            }
            catch(def e)
            {

            }

            vcardList.each{vcard->
                def url = vcard.getField("URL")
                if (url)
                {
                    federatedServer = new FederatedServer(
                            [serverId:vcard.getField("IP")+"@${jabberDomain}",//"${application.config.omar.serverURL}",
                                    available:true,
                                    vcard: vcard.toString()
                            ])
                    try{
                        federatedServer.save()
                    }
                    catch(def e)
                    {

                    }
                }
            }
        }
    }
    def makeAvailable(def userName)
    {
        def fullUser = userName + "@" + jabberDomain
        def tempCard = new VCard();
        try{
            tempCard.load(jabber.connection, fullUser);
            def ip =  tempCard.getField("IP");
            if(ip)
            {
                FederatedServer.withTransaction{
                    def federatedServer = new FederatedServer([serverId:fullUser,
                                                           available: true,
                                                           vcard: tempCard.toString()])
                    federatedServer.save()
                }
            }
        }
        catch(def e)
        {
        }
    }
    def makeUnavailable(def userName)
    {
        def fullUser = userName + "@" + jabberDomain
        FederatedServer.withTransaction{
            FederatedServer.where{serverId==fullUser}.deleteAll()
        }
    }
    def getServerList()
    {
        def result = []
        FederatedServer.withTransaction{
            FederatedServer.findAll(sort:"id", order: 'asc').each{server->
                def vcard = VCardProvider.createVCardFromXML(server.vcard)
                result << [
                        id: server.serverId.replaceAll(~/\@|\./, ""),
                        firstName:vcard.firstName,
                        lastName:vcard.lastName,
                        nickname:vcard.nickName,
                        organization:vcard.organization,
                        ip:vcard.getField("IP"),
                        url:vcard.getField("URL"),
                        phone:vcard.getPhoneHome("VOICE")?:vcard.getPhoneWork("VOICE")
                ]
            }
        }

        result
    }
    def reconnect()
    {
        if (isConnected())
        {
            disconnect()
        }
        connect()
    }
    def disconnect()
    {
        try{

            if(isConnected())
            {
                jabber.chatRoom.removeParticipantListener(participantListener)
                jabber?.connection.disconnect()
            }
        }
        catch(def e)
        {

        }
        jabber = [:]
    }
    def connect()
    {
        if (isConnected())
        {
            return jabber
        }
        jabber = [:]
        jabber.config     =  new ConnectionConfiguration(jabberDomain,
                                                         jabberPort);
        jabber.connection = new XMPPConnection(jabber.config);
        try{
            jabber.connection.connect();
            jabber.connection.login(vCard.getField("IP"), "abc123!@#")
        }
        catch(def e)
        {
            try{
                jabber.connection.disconnect()
                jabber.connection.connect()
                jabber.connection.login(jabberAdminUser, jabberAdminPassword);
                jabber.connection.accountManager.createAccount(vCard.getField("IP"),
                        "abc123!@#",
                        [name:vCard.getField("IP")]);
                jabber.connection.disconnect()
                jabber.connection.connect()
                jabber.connection.login(vCard.getField("IP"), "abc123!@#")
            }
            catch(def e2)
            {
                println e2
            }
        }
        if (jabber.connection.isAuthenticated())
        {
            try{

                vCard.save(jabber.connection)
                jabber.chatRoom = new MultiUserChat(jabber.connection,
                        "${jabberChatRoomId}")
                jabber.chatRoom.join(vCard.getField("IP"),
                        "${jabberChatRoomPassword}")

                participantListener = new JabberParticipantListener([federatedServerService:this])
                jabber.chatRoom.addParticipantListener(participantListener)
            }
            catch(def e)
            {

            }
        }

        return jabber
    }
    def createAdminConnection()
    {
        def result = [connection:null,
                      config: null,
                      chatRoom: null]
        try{

            def config     =  new ConnectionConfiguration(jabberDomain,
                                                          jabberPort);
            def connection = new XMPPConnection(config);
            connection.connect();
            connection.login(jabberAdminUser, jabberAdminPassword);
            def chatRoom = new MultiUserChat(connection,
                    "${jabberChatRoomId}")
            chatRoom.join(jabberAdminUser,
                    "${jabberChatRoomPassword}")
            result = [
                    connection:connection,
                    config:config,
                    chatRoom:chatRoom
            ]
        }
        catch(def e)
        {

        }

        result
    }
    def getAllVCards()
    {
        def adminConnection = createAdminConnection()
        def vCards = []

        if (adminConnection.connection?.isConnected())
        {
            def occupants = adminConnection.chatRoom.participants
            occupants.each{
                def user = it.jid.split("/")[0]
                def vCard = new VCard()
                vCard.load(adminConnection.connection, user)
                vCards << vCard
            }
            adminConnection.connection.disconnect();
        }
        vCards
    }

    void afterPropertiesSet() throws Exception {
        loadFromConfig(grailsApplication.config)
        connect()
        if(isConnected())
        {
            refreshServerTable()
        }
    }
}
