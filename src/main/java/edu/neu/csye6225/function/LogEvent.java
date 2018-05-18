package edu.neu.csye6225.function;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.Optional;
import java.util.Random;

public class LogEvent implements RequestHandler<SNSEvent, Object> {

  DynamoDB dynamoDB;
  Regions regions = Regions.US_EAST_1;
  String tableName = "csye6225";



  public Object handleRequest(SNSEvent request, Context context) {

      String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());

      context.getLogger().log("Lambda invoked : " + timeStamp);


      String payload = request.getRecords().get(0).getSNS().getMessage();





      //////////////////////////////////////////////////////////////////////
      context.getLogger().log("initializing db");
      this.initDynamoDB();

      Table table = dynamoDB.getTable(tableName);

      Optional<Item> item = Optional.ofNullable(table.getItem("id", payload));

      long TTLepochTime = Instant.now().getEpochSecond();
      TTLepochTime += 1200;

      String token = getToken(payload, TTLepochTime);





      if(!(item.isPresent())){
          context.getLogger().log("Generating password reset record");
//          context.getLogger().log("Payload "+ payload+" token "+ token+" epochTime "+TTLepochTime);

          Item saveItem = new Item()
                  .withPrimaryKey("id",payload)
                  .withString("token",token)
                  .withNumber("tokenTTL",TTLepochTime);
          table.putItem(saveItem);

          context.getLogger().log("Password reset record saved to database");




            String dn = System.getenv("domainName");

          try{
              String mailText = "<center><img src=\"https://www.censornet.com/media/2777/product_icon_passwordreset.png\" style=\"width:100px;height:100px\"><h1>Password Change Request</h1></center>" +
                      "<p>Hi,</p><p> We received a request to reset the password for the Application account associated with this e-mail address. Click the link below to reset your password using our secure server</p><p> <a href = \"https://"+dn+"/reset?token="+token+"&email="+payload+"\"><button>Reset Password</button></a></p>";
//              context.getLogger().log("Link is : "+ mailText);
              sendMail(payload,"do-not-reply@"+dn, mailText,"Password reset request");
              context.getLogger().log("Mail Sent with password reset link");
//              context.getLogger().log("Payload "+ payload+" token "+ token+" epochTime "+TTLepochTime);

          }catch (Exception ex){
              context.getLogger().log("Error : "+ ex.toString());

          }

      }
      else{
          //context.getLogger().log("Record already exists for " + payload + "with token "+ token+ " taking no action");
          context.getLogger().log("Record already exists for requested account, taking no action");

      }





//    context.getLogger().log("1: " + (request == null));

//    context.getLogger().log("2: " + (request.getRecords().size()));




//    context.getLogger().log(payload);

    timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());

    context.getLogger().log("Lambda Completed: " + timeStamp);

    //PutItemOutcome outC = this.dynamoDB.getTable("csye6225").putItem(new PutItemSpec().withItem(new Item().withString("id",payload)));




    return null;
  }


  private String getToken(String email, long epTime){
      long token = 17;
      token *= 31;
      token += email.hashCode();
      token *= 31;
      token += ((Long)epTime).hashCode();
      Random random = new Random(epTime);
      token *= 31;
      token += random.nextLong();
      String tok = Long.toHexString(token);
      return tok;

  }



  private void initDynamoDB(){
      AmazonDynamoDB client = AmazonDynamoDBClientBuilder
              .standard()
              .withRegion(regions)
              .build();
      this.dynamoDB = new DynamoDB(client);

  }

  private void sendMail(String to, String from, String mailText,String sub){
      Destination destination = new Destination().withToAddresses(to);
      Content bodyContent = new Content()
              .withCharset("UTF-8")
              .withData(mailText);

      Body body = new Body().withHtml(bodyContent);

      Content subContent = new Content()
              .withCharset("UTF-8")
              .withData(sub);



      Message message = new Message()
              .withBody(body)
              .withSubject(subContent);



      AmazonSimpleEmailService mailClient = AmazonSimpleEmailServiceAsyncClientBuilder
              .standard()
              .withRegion(regions)
              .build();

      SendEmailRequest emailRequest = new SendEmailRequest()
              .withDestination(destination)
              .withMessage(message)
              .withSource(from);


      SendEmailResult result = mailClient.sendEmail(emailRequest);

  }

}

